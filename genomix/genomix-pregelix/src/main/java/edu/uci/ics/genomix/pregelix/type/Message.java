package edu.uci.ics.genomix.pregelix.type;

public class Message {

    public static final byte NON = 0;
    public static final byte START = 1;
    public static final byte END = 2;
    public static final byte STOP = 3;
    public static final byte FROMPSEUDOHEAD = 4;
    public static final byte FROMPSEUDOREAR = 5;
    public static final byte FROMSELF = 6;

    public final static class MESSAGE_CONTENT {

        public static String getContentFromCode(byte code) {
            String r = "";
            switch (code) {
                case NON:
                    r = "NON";
                    break;
                case START:
                    r = "START";
                    break;
                case END:
                    r = "END";
                    break;
                case STOP:
                    r = "STOP";
                    break;
                case FROMPSEUDOHEAD:
                    r = "FROMPSEUDOHEAD";
                    break;
                case FROMPSEUDOREAR:
                    r = "FROMPSEUDOREAR";
                    break;
                case FROMSELF:
                    r = "FROMSELF";
                    break;
            }
            return r;
        }
    }
}