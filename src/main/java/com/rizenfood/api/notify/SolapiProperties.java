package com.rizenfood.api.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix ="app.alimtalk.solapi")
public record SolapiProperties (
        String apiKey,
        String apiSecret,
        String pfId,
        String from){
    public SolapiProperties{
        apiKey = trim(apiKey);
        apiSecret = trim(apiSecret);
        pfId = trim(pfId);
        from = trim(from).replaceAll("\\D", "");
    }
    public boolean ready(){
        return !apiKey.isEmpty() && !apiSecret.isEmpty() && !pfId.isEmpty() && !from.isEmpty();
    }
    private static String trim(String v){
        return v == null ? "" : v.trim();
    }
}
