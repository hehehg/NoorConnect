package com.noorconnect.desktop;

import org.drinkless.tdlib.Client;

public final class TdLibNativeSmoke {
    private TdLibNativeSmoke() {
    }

    public static void main(String[] args) {
        Client.create(null, null, null);
        System.out.println("TDLib JNI client created successfully.");
    }
}