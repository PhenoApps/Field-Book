package com.fieldbook.tracker.brapi;

import java.util.HashMap;
import java.util.Map;

public enum ApiErrorCode {
    BAD_REQUEST(400),
    UNAUTHORIZED(401),
    NOT_FOUND(404),
    FORBIDDEN(403);

    private static final Map<Integer, ApiErrorCode> apiErrorsByCode = new HashMap<>();

    static {
        for (ApiErrorCode apiErrorCode : ApiErrorCode.values()) {
            apiErrorsByCode.put(apiErrorCode.code, apiErrorCode);
        }
    }

    private final int code;

    ApiErrorCode(int code) {
        this.code = code;
    }

    public static ApiErrorCode processErrorCode(Integer code) {
        return apiErrorsByCode.get(code);
    }
}
