package com.pesitwizard.client.pesit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PesitStateMachine {
    private volatile ClientState currentState = ClientState.CN01_REPOS;

    public synchronized ClientState getCurrentState() {
        return currentState;
    }

    public synchronized void transition(ClientState newState) {
        if (!currentState.canTransitionTo(newState)) {
            throw new IllegalStateException(currentState.getCode() + " -> " + newState.getCode());
        }
        log.debug("State: {} -> {}", currentState.getCode(), newState.getCode());
        currentState = newState;
    }

    public synchronized void reset() {
        currentState = ClientState.CN01_REPOS;
    }

    public synchronized void error() {
        currentState = ClientState.ERROR;
    }

    public synchronized boolean canSendData() {
        return currentState.canSendData();
    }

    public synchronized boolean canReceiveData() {
        return currentState.canReceiveData();
    }

    public synchronized boolean isConnected() {
        return currentState.isConnected();
    }
}
