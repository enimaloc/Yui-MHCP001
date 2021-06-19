package com.github.enimaloc.yui.utils;

import java.util.Optional;

public class IntUtils {
    
    public static Optional<Integer> getInt(String s) {
        try {
            return Optional.of(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
