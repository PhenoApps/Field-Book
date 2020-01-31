package com.fieldbook.tracker.brapi;

// Allows a message to be passed back to the view
public class BrapiControllerResponse {

    public Boolean status;
    public String message;

    BrapiControllerResponse(Boolean status, String message) {

        this.status = status;
        this.message = message;
    }

    public Boolean getStatus() {
        return this.status;
    }

    public String getMessage() {
        return this.message;
    }
}
