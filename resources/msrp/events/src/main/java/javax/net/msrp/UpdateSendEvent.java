/**
 * 
 */
package javax.net.msrp;

/**
 * @author tuijldert
 *
 */
@SuppressWarnings("serial")
public class UpdateSendEvent extends BaseEvent {
    private Message message;
    private long bytesSent;

    /**
     * @param session
     */
    public UpdateSendEvent(Session session, Message message, long bytesSent) {
        super(session);
        this.message = message;
        this.bytesSent = bytesSent;
    }

    /**
     * @return the message
     */
    public Message getMessage() {
        return message;
    }

    /**
     * @return bytes sent so far
     */
    public long getBytesSent() {
        return bytesSent;
    }
}
