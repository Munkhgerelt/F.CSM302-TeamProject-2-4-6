package sorting.input;

/** Invalid user input; the message is meant to be shown to the user as-is. */
public final class InputException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public InputException(String message) {
        super(message);
    }
}
