package com.fieldbook.tracker.brapi;

import java.util.HashMap;
import java.util.Map;

public enum ApiError {
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    NOT_FOUND(404),
    FORBIDDEN(403);

    private static final Map<Integer, ApiError> apiErrorsByCode = new HashMap<>();

    static {
        for (ApiError apiError : ApiError.values()) {
            apiErrorsByCode.put(apiError.code, apiError);
        }
    }

    private final int code;

    ApiError(int code) {
        this.code = code;
    }

    public static ApiError processErrorCode(Integer code) {
        return apiErrorsByCode.get(code);
    }
}
