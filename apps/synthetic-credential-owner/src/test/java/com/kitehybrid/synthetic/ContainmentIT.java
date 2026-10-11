package com.kitehybrid.synthetic;

import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import com.github.dockerjava.api.model.Capability;
import static org.junit.jupiter.api.Assertions.*;

/** Kernel-policy probes in an owned cached image. This is NOT the Java owner deployment. */
class ContainmentIT {
    static GenericContainer<?> sandbox(long pids) {
        return new GenericContainer<>("postgres:17.6").withImagePullPolicy(ignored->false)
            .withNetworkMode("none").withCreateContainerCmdModifier(cmd->{
                cmd.withUser("65534:65534").withEntrypoint("/bin/sleep");
                cmd.getHostConfig().withReadonlyRootfs(true).withCapDrop(Capability.ALL)
                    .withSecurityOpts(List.of("no-new-privileges:true")).withMemory(67108864L)
                    .withNanoCPUs(500000000L).withPidsLimit(pids)
                    .withTmpFs(Map.of("/scratch","rw,noexec,nosuid,nodev,size=1m,mode=1777",
                        "/var/lib/postgresql/data","ro,noexec,nosuid,nodev,size=1m"));
            }).withCommand("120");
    }
    static void inspect(GenericContainer<?> container,long pids) {
        var info=container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec();var host=info.getHostConfig();
        assertEquals("none",host.getNetworkMode());assertEquals(Boolean.TRUE,host.getReadonlyRootfs());
        assertEquals("65534:65534",info.getConfig().getUser());assertEquals(pids,host.getPidsLimit());
        assertEquals(67108864L,host.getMemory());assertEquals(500000000L,host.getNanoCPUs());
        assertTrue(host.getSecurityOpts().contains("no-new-privileges:true"));assertTrue(Arrays.asList(host.getCapDrop()).contains(Capability.ALL));
        assertTrue(host.getBinds()==null||host.getBinds().length==0);
        if(info.getMounts()!=null)for(var mount:info.getMounts()) {
            assertTrue(mount.getSource()==null||mount.getSource().isBlank());
            assertTrue(mount.getName()==null||mount.getName().isBlank());
            assertTrue(Set.of("/scratch","/var/lib/postgresql/data").contains(mount.getDestination().getPath()));
        }
    }
    @Test void readOnlyUnprivilegedNetworkNoneKernelPolicyIsEnforced() throws Exception {
        try(var container=sandbox(16)) {
            container.start();inspect(container,16);
            var result=container.execInContainer("/bin/bash","-c","""
                set -eu
                [ "$UID" != 0 ]
                caps=missing; priv=missing
                while read -r name value rest; do
                  case "$name" in CapEff:) caps="$value";; NoNewPrivs:) priv="$value";; esac
                done < /proc/self/status
                [ "$caps" = 0000000000000000 ]; [ "$priv" = 1 ]
                if (printf x > /synthetic-root-write) 2>/dev/null; then exit 31; fi
                printf SYNTHETIC_ONLY > /scratch/allowed
                [ -r /scratch/allowed ]
                [ ! -e /host ]; [ ! -e /mnt/c ]; [ ! -e /var/run/docker.sock ]
                [ -z "${JAVA_TOOL_OPTIONS+x}" ]; [ -z "${SYNTHETIC_HOST_SECRET+x}" ]
                while read -r iface rest; do [ "$iface" = Iface ] || exit 32; done < /proc/net/route
                if timeout 2 bash -c 'exec 3<>/dev/tcp/192.0.2.1/9' >/dev/null 2>&1; then exit 33; fi
                if timeout 2 getent ahostsv4 synthetic-fixture.invalid >/dev/null 2>&1; then exit 34; fi
                printf SYNTHETIC_KERNEL_POLICY_PASS
                """);
            assertEquals(0,result.getExitCode(),"SYNTHETIC_KERNEL_POLICY_FAILED");
            assertEquals("SYNTHETIC_KERNEL_POLICY_PASS",result.getStdout());assertEquals("",result.getStderr());
            assertEquals("",container.getLogs());
        }
    }
    @Test void pidCgroupDeniesChildProcessCreation() throws Exception {
        try(var container=sandbox(1).withCreateContainerCmdModifier(cmd->cmd.withEntrypoint("/bin/bash"))
            .withCommand("-c","/bin/true; code=$?; if [ \"$code\" = 0 ]; then exit 35; fi; printf SYNTHETIC_FORK_DENIED")
            .withStartupCheckStrategy(new OneShotStartupCheckStrategy(){
                @Override public StartupStatus checkStartupState(com.github.dockerjava.api.DockerClient docker,String id) {
                    var state=getCurrentState(docker,id);
                    return "exited".equals(state.getStatus())?StartupStatus.SUCCESSFUL:StartupStatus.NOT_YET_KNOWN;
                }
            }.withTimeout(Duration.ofSeconds(25)))) {
            container.start();inspect(container,1);
            var state=container.getDockerClient().inspectContainerCmd(container.getContainerId()).exec().getState();
            assertFalse(Boolean.TRUE.equals(state.getOOMKilled()));
            assertNotEquals(0L,state.getExitCodeLong());
            String logs=container.getLogs();assertTrue(logs.contains("fork"));assertTrue(logs.contains("Resource temporarily unavailable"));
            assertFalse(logs.contains("SYNTHETIC_HOST_SECRET"));
        }
    }
}
