package com.fieldbook.tracker.brapi;

// Allows a message and optional data to be passed back to the view
public class BrapiControllerResponse<T> {

    public Boolean status;
    public String message;
    private T data; // Generic data element

    // Constructor without data
    public BrapiControllerResponse(Boolean status, String message) {
        this.status = status;
        this.message = message;
        this.data = null; // No data
    }

    // Constructor with data
    public BrapiControllerResponse(Boolean status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data; // Include data
    }

    public Boolean getStatus() {
        return this.status;
    }

    public String getMessage() {
        return this.message;
    }

    public T getData() {
        return data;
    }
}
