package io.quarkus.qe.test.failure.detector.output.impl;

import io.quarkus.qe.test.failure.detector.logger.Logger;
import io.quarkus.qe.test.failure.detector.output.Data;
import io.quarkus.qe.test.failure.detector.output.OutputChannel;
import jakarta.inject.Singleton;

import java.io.IOException;

@Singleton
final class OutputChannelImpl implements OutputChannel {

    private final Logger logger;

    OutputChannelImpl(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void process(Data data) {
        try (var reader = data.reader()) {
            logger.info(reader.readAllAsString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
