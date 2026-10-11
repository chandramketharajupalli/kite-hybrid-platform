package com.kitehybrid.synthetic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlatformResourcesTest {
    @Test void fourthWorkloadAndInvalidCountsAreDenied() {
        for(int count:new int[]{-1,3,4,Integer.MAX_VALUE})assertThrows(AssertionError.class,()->PlatformResources.requireBudget(count));
    }
    @Test void onlyThreeSlotsMayBeReserved() {
        for(int count:new int[]{0,1,2})assertDoesNotThrow(()->PlatformResources.requireBudget(count));
    }
}
