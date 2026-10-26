package io.github.recrivenvi.minecraftprotocol.core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** Taps published server logging, without replacing terminal appenders or reading log files. */
public final class ConsoleLogCapture extends AbstractAppender implements AutoCloseable {
    private final ServerConsole console;
    private final Logger root;

    public ConsoleLogCapture(ServerConsole console) {
        super(
                "MineCraftProtocolConsole-" + java.util.UUID.randomUUID(),
                null,
                null,
                true,
                Property.EMPTY_ARRAY);
        this.console = console;
        this.root = (Logger) LogManager.getRootLogger();
        start();
        root.addAppender(this);
    }

    @Override
    public void append(LogEvent event) {
        if (!event.getLevel().isMoreSpecificThan(Level.INFO)) return;
        String text = event.getMessage().getFormattedMessage();
        if (event.getThrown() != null) text += "\n" + event.getThrown();
        console.publish(
                event.getLevel().name(), event.getLoggerName(), event.getThreadName(), text, null);
    }

    @Override
    public void close() {
        root.removeAppender(this);
        stop();
    }
}
