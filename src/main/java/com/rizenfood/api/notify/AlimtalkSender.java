package com.rizenfood.api.notify;

public interface AlimtalkSender {
    String provider();
    void send(AlimtalkMessage message);
    class AlimtalkException extends RuntimeException{
        public AlimtalkException(String message){
            super(message);
        }
        public AlimtalkException(String message, Throwable cause){
            super(message, cause);
        }
    }
}
