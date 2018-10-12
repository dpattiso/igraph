package recogniser.util;

public class RecognitionException extends Exception
{

	public RecognitionException()
	{
	}

	public RecognitionException(String message)
	{
		super(message);
	}

	public RecognitionException(Throwable cause)
	{
		super(cause);
	}

	public RecognitionException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public RecognitionException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace)
	{
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
