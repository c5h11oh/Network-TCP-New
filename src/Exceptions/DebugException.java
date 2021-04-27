package Exceptions;

public class DebugException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 389439821L;

    public DebugException() {
        super();
    }

    public DebugException(String message) {
        super(message);
    }
    
    public String toString(){
        return "Trace your code";
    }
    
}
