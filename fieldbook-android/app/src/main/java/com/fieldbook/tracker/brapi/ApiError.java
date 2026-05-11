package com.fieldbook.tracker.brapi;

public class ApiError {
    private ApiErrorCode errorCode;
    private String responseBody;

    public ApiErrorCode getErrorCode() {
        return errorCode;
    }

    public ApiError setErrorCode(ApiErrorCode errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public ApiError setResponseBody(String responseBody) {
        this.responseBody = responseBody;
        return this;
    }
}
