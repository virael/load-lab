package com.loadlab.sut;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

// Standalone MockMvc over the real controller and a real SimulationState — no Spring
// context. The point of this class is the hand-written validation in updateConfig() that
// @Valid could not express: the errorRate range and the min<=max cross-check. Both
// rejection branches, and the current-state fallback used by that cross-check on a partial
// update, had no test before.
class ConfigControllerTest {

  // Starts at min=10, max=100, errorRate=0.0 so the fallback branches (a null field in the
  // request coalescing to the current value) have distinct, checkable values.
  private MockMvc mvc() {
    SimulationState state = new SimulationState(new SimulationProperties(10, 100, 0.0));
    return MockMvcBuilders.standaloneSetup(new ConfigController(state)).build();
  }

  @ParameterizedTest
  @ValueSource(doubles = {1.5, -0.5})
  void rejectsErrorRateOutsideValidRange(double errorRate) throws Exception {
    mvc()
        .perform(
            post("/simulate/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorRate\": " + errorRate + "}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("errorRate must be between 0 and 1"));
  }

  @Test
  void rejectsMinLatencyGreaterThanMaxLatency() throws Exception {
    mvc()
        .perform(
            post("/simulate/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"minLatencyMs\": 500, \"maxLatencyMs\": 100}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("minLatencyMs must be <= maxLatencyMs"));
  }

  @Test
  void rejectsPartialUpdateWhenNewMinExceedsCurrentMax() throws Exception {
    // Only minLatencyMs is sent; maxLatencyMs must fall back to the current 100. 500 > 100,
    // so this proves the cross-check reads live state, not just the two request fields.
    mvc()
        .perform(
            post("/simulate/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"minLatencyMs\": 500}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("minLatencyMs must be <= maxLatencyMs"));
  }

  @Test
  void acceptsPartialUpdateChangingOnlyErrorRate() throws Exception {
    // A valid partial update returns 200 and echoes the merged config: errorRate changed,
    // latency bounds left untouched.
    mvc()
        .perform(
            post("/simulate/config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"errorRate\": 0.5}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minLatencyMs").value(10))
        .andExpect(jsonPath("$.maxLatencyMs").value(100))
        .andExpect(jsonPath("$.errorRate").value(0.5));
  }

  @Test
  void returnsCurrentConfig() throws Exception {
    mvc()
        .perform(get("/simulate/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minLatencyMs").value(10))
        .andExpect(jsonPath("$.maxLatencyMs").value(100))
        .andExpect(jsonPath("$.errorRate").value(0.0));
  }
}
