package com.loadlab.sut;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// Standalone MockMvc over the real /simulate endpoint. The latency and error-injection
// branches are driven to their deterministic extremes so the assertions are stable despite
// the ThreadLocalRandom inside: errorRate 0.0 never fails, 1.0 always fails, and min==max
// takes the constant-latency path while min<max takes the randomised one.
class SimulationControllerTest {

  private MockMvc mvcWith(long min, long max, double errorRate) {
    SimulationState state = new SimulationState(new SimulationProperties(min, max, errorRate));
    return MockMvcBuilders.standaloneSetup(new SimulationController(state)).build();
  }

  @Test
  void returnsOkWhenErrorRateIsZeroAndLatencyIsConstant() throws Exception {
    // min == max == 0: constant-latency branch, no sleep; nextDouble() < 0.0 is never true.
    mvcWith(0, 0, 0.0)
        .perform(get("/simulate"))
        .andExpect(status().isOk())
        .andExpect(content().string("ok"));
  }

  @Test
  void alwaysFailsWhenErrorRateIsOne() throws Exception {
    // nextDouble() is in [0,1), so < 1.0 always holds: the failure branch fires every time.
    mvcWith(0, 0, 1.0)
        .perform(get("/simulate"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().string("simulated failure"));
  }

  @Test
  void usesRandomisedLatencyWhenRangeIsNonZero() throws Exception {
    // min < max: the else branch draws from [min, max]. Bounds kept tiny (0..1 ms) so the
    // real Thread.sleep stays fast while still exercising the range path.
    mvcWith(0, 1, 0.0)
        .perform(get("/simulate"))
        .andExpect(status().isOk())
        .andExpect(content().string("ok"));
  }
}
