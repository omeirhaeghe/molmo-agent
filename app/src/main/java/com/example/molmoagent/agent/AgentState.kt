package com.example.molmoagent.agent

enum class AgentState {
    IDLE,
    OBSERVING,
    REASONING,
    ACTING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    ERROR,
    MAX_STEPS_REACHED
}
