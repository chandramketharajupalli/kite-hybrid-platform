package com.kitehybrid.synthetic;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackagingTest {
    @Test void compiledRuntimeHasNoForbiddenDependenciesOrEntrypoint() throws Exception {
        Path classes=Path.of(SyntheticOwner.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try(var files=Files.walk(classes)) {
            for(Path p:files.filter(f->f.toString().endsWith(".class")).toList()) {
                String bytes=new String(Files.readAllBytes(p),StandardCharsets.ISO_8859_1);
                for(String forbidden:new String[]{"org/springframework","java/sql","javax/sql","org/postgresql","redis/","com/kitehybrid/platform","api.kite.trade"})
                    assertFalse(bytes.contains(forbidden),"FORBIDDEN_RUNTIME_DEPENDENCY");
            }
        }
        for(Class<?> c:new Class<?>[]{SyntheticOwner.class,SyntheticHalt.class,LoopbackPeer.class}) {
            assertFalse(Modifier.isPublic(c.getModifiers()));
            for(var m:c.getDeclaredMethods())assertNotEquals("main",m.getName());
        }
    }
    @Test void tradingAndSpringClassesAreAbsent() {
        for(String type:new String[]{"com.kitehybrid.platform.broker.infrastructure.kite.KiteSession","com.kitehybrid.platform.broker.infrastructure.kite.KiteRestTransport","com.kitehybrid.platform.broker.application.auth.KiteAuthenticationUseCase","org.springframework.boot.SpringApplication"})
            assertThrows(ClassNotFoundException.class,()->Class.forName(type));
    }
    @Test void standaloneModuleIsNotInTradingReactorOrDependencies() throws Exception {
        Path root=Path.of("../..").toAbsolutePath().normalize();
        assertFalse(Files.readString(root.resolve("pom.xml")).contains("<module>apps/synthetic-credential-owner</module>"));
        assertFalse(Files.readString(root.resolve("apps/trading-core/pom.xml")).contains("synthetic-credential-owner"));
        String pom=Files.readString(Path.of("pom.xml"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var doc=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(pom.getBytes(StandardCharsets.UTF_8)));
        var dependencies=doc.getElementsByTagName("dependency");assertEquals(3,dependencies.getLength());
        for(int i=0;i<dependencies.getLength();i++)assertEquals("test",((org.w3c.dom.Element)dependencies.item(i)).getElementsByTagName("scope").item(0).getTextContent());
    }
}
