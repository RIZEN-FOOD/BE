package com.rizenfood.api.notify;

import java.util.Map;

public record AlimtalkMessage(
        String to,
        AlimtalkTemplate template,
        String templateCode,
        Map<String, String> variables,
        String text,
        String buttonUrl
) {
    public String masked(){
        if(to == null || to.length() < 7){
            return "***";
        }
        return to.substring(0, 3) + "****" + to.substring(to.length() - 2);
    }
    @Override
    public String toString(){
        return "AlimtalkMessage[" + template + " → " + masked() + "]";
    }
}
