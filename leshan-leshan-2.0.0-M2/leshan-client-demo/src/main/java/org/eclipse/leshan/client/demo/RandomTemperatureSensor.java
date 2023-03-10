package org.eclipse.leshan.client.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.leshan.client.demo.core.instance.BaseInstanceEnabler;
import org.eclipse.leshan.client.demo.clientcore.servers.ServerIdentity;
import org.eclipse.leshan.core.Destroyable;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;

public class RandomTemperatureSensor extends BaseInstanceEnabler implements Destroyable {

    private static final Logger LOG = LoggerFactory.getLogger(RandomTemperatureSensor.class);

    private static final String UNIT_CELSIUS = "cel";
    private static final int SENSOR_VALUE = 5700;
    private static final int UNITS = 5701;
    private static final int MAX_MEASURED_VALUE = 5602;
    private static final int MIN_MEASURED_VALUE = 5601;
    private static final int RESET_MIN_MAX_MEASURED_VALUES = 5605;
    private static final List<Integer> supportedResources = Arrays.asList(SENSOR_VALUE, UNITS, MAX_MEASURED_VALUE,
            MIN_MEASURED_VALUE, RESET_MIN_MAX_MEASURED_VALUES);
    private final ScheduledExecutorService scheduler;
    private final Random rng = new Random();
    private double currentTemp = 20d;
    private double minMeasuredValue = currentTemp;
    private double maxMeasuredValue = currentTemp;

    public RandomTemperatureSensor() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Temperature Sensor"));
        scheduler.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                adjustTemperature();
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    @Override
    public synchronized ReadResponse read(ServerIdentity identity, int resourceId) {
        LOG.info("Read on Temperature resource /{}/{}/{}", getModel().id, getId(), resourceId);
        switch (resourceId) {
            case MIN_MEASURED_VALUE:
                return ReadResponse.success(resourceId, getTwoDigitValue(minMeasuredValue));
            case MAX_MEASURED_VALUE:
                return ReadResponse.success(resourceId, getTwoDigitValue(maxMeasuredValue));
            case SENSOR_VALUE:
                return ReadResponse.success(resourceId, getTwoDigitValue(currentTemp));
            case UNITS:
                return ReadResponse.success(resourceId, UNIT_CELSIUS);
            default:
                return super.read(identity, resourceId);
        }
    }

    @Override
    public synchronized ExecuteResponse execute(ServerIdentity identity, int resourceId, String params) {
        LOG.info("Execute on Temperature resource /{}/{}/{}", getModel().id, getId(), resourceId);
        switch (resourceId) {
            case RESET_MIN_MAX_MEASURED_VALUES:
                resetMinMaxMeasuredValues();
                return ExecuteResponse.success();
            default:
                return super.execute(identity, resourceId, params);
        }
    }

    private double getTwoDigitValue(double value) {
        BigDecimal toBeTruncated = BigDecimal.valueOf(value);
        return toBeTruncated.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private void adjustTemperature() {
        float delta = (rng.nextInt(20) - 10) / 10f;
        currentTemp += delta;
        Integer changedResource = adjustMinMaxMeasuredValue(currentTemp);
        if (changedResource != null) {
            //fireResourcesChange(SENSOR_VALUE, changedResource);
        } else {
            //fireResourcesChange(SENSOR_VALUE);
        }
    }

    private synchronized Integer adjustMinMaxMeasuredValue(double newTemperature) {
        if (newTemperature > maxMeasuredValue) {
            maxMeasuredValue = newTemperature;
            return MAX_MEASURED_VALUE;
        } else if (newTemperature < minMeasuredValue) {
            minMeasuredValue = newTemperature;
            return MIN_MEASURED_VALUE;
        } else {
            return null;
        }
    }

    private void resetMinMaxMeasuredValues() {
        minMeasuredValue = currentTemp;
        maxMeasuredValue = currentTemp;
    }

    @Override
    public List<Integer> getAvailableResourceIds(ObjectModel model) {
        return supportedResources;
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
    }

    public static void main(String[] args) {
        byte[] a = {72, 1, -84, -93, 103, -43, 94, -110, 57, -87, 1, 59, 96, 81, 54, 1, 48, 98, 45, 22};
        byte[] b = {104, 69, 54, 63, 65, 18, -113, 118, 94, -44, 43, 98, 96, 98, 45, 22, -1, -56, 0, 8, -64, 83, 0, 0, 0, 0, 0, 0, -56, 1, 8, 64, 87, 0, 0, 0, 0, 0, 0, -60, 5, 99, -101, -32, -112};
        Byte [] c = {-64, 53, 0, 0, 0, 0, 0, 0};
        String s = DatatypeConverter.printHexBinary(a);
        String s1 = DatatypeConverter.printHexBinary(b);
        Number number=27.0;
        int value;

        ByteBuffer fBuf;
        if (number instanceof Float) {
            fBuf = ByteBuffer.allocate(4);
            fBuf.putFloat(number.floatValue());
        } else {
            fBuf = ByteBuffer.allocate(8);
            fBuf.putDouble(number.doubleValue());
        }
        byte[] array = fBuf.array();

        System.out.println(s);
        System.out.println(s1);
    }
}
