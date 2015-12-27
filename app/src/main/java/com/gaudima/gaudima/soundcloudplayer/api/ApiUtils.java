package com.gaudima.gaudima.soundcloudplayer.api;

import java.util.concurrent.atomic.AtomicReference;

public final class ApiUtils {
    private static AtomicReference<StringBuilder> stringBuilderRef = new AtomicReference<>();

    public static StringBuilder getStringBuilder() {
        StringBuilder stringBuilder = stringBuilderRef.get();
        if(stringBuilder == null) {
            stringBuilder = new StringBuilder();

        }
        return stringBuilder;
    }

    public static void releaseStringBuilder(StringBuilder stringBuilder) {
        stringBuilder.setLength(0);
        stringBuilderRef.set(stringBuilder);
    }
}
