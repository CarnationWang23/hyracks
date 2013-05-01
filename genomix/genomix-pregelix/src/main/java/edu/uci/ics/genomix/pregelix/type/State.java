package edu.uci.ics.genomix.pregelix.type;

public class State {
	
	public static final byte NON_VERTEX = 0;
	public static final byte START_VERTEX = 1;
	public static final byte END_VERTEX = 2;
	public static final byte MID_VERTEX = 3;
	public static final byte TODELETE = 4;
	public static final byte FINAL_VERTEX = 5;
	public static final byte FINAL_DELETE = 6;
	public static final byte CYCLE = 7;
	
	public final static class STATE_CONTENT{

		public static String getContentFromCode(byte code){
			String r = "";
			switch(code){
			case NON_VERTEX:
				r = "NON_VERTEX";
				break;
			case START_VERTEX:
				r = "START_VERTEX";
				break;
			case END_VERTEX:
				r = "END_VERTEX";
				break;
			case MID_VERTEX:
				r = "MID_VERTEX";
				break;
			case TODELETE:
				r = "TODELETE";
				break;
			case FINAL_VERTEX:
				r = "FINAL_VERTEX";
				break;
			case FINAL_DELETE:
				r = "FINAL_DELETE";
				break;
			case CYCLE:
				r = "CYCLE";
				break;
			}
			return r;
		}
	}
}
