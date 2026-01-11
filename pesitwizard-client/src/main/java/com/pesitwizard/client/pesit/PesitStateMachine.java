package com.pesitwizard.client.pesit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PesitStateMachine {
    @Getter
    private ClientState currentState = ClientState.CN01_REPOS;

    public void transition(ClientState newState) {
        if (!currentState.canTransitionTo(newState)) {
            throw new IllegalStateException(currentState.getCode() + " -> " + newState.getCode());
        }
        log.debug("State: {} -> {}", currentState.getCode(), newState.getCode());
        currentState = newState;
    }

    public void reset() {
        currentState = ClientState.CN01_REPOS;
    }

    public void error() {
        currentState = ClientState.ERROR;
    }

    public boolean canSendData() {
        return currentState.canSendData();
    }

    public boolean canReceiveData() {
        return currentState.canReceiveData();
    }

    public boolean isConnected() {
        return currentState.isConnected();
    }
}
