package io.github.frc5024.lib5k.hardware.ni.roborio;

import edu.wpi.first.hal.can.CANStatus;
import edu.wpi.first.wpilibj.Notifier;
import edu.wpi.first.wpilibj.RobotController;
import io.github.frc5024.lib5k.logging.RobotLogger;
import io.github.frc5024.lib5k.logging.RobotLogger.Level;
import io.github.frc5024.lib5k.csvlogging.StatusLogger;
import io.github.frc5024.lib5k.csvlogging.LoggingObject;
import io.github.frc5024.lib5k.hardware.ni.roborio.fpga.RR_HAL;

/**
 * Utility for tracking and reporting RoboRIO FPGA faults.
 */
public class FaultReporter {
    // Config
    private final double canAcceptableUsage = 0.9;

    // locals
    RobotLogger logger = RobotLogger.getInstance();
    private static FaultReporter instance = null;
    private Notifier thread;

    // Fault counts
    int count3v3, count5v, count6v = 0;
    boolean lastBrownoutState, lastSystemState = false;
    boolean last3v3Enabled, last5vEnabled, last6vEnabled = false;
    CANStatus lastCANStatus = new CANStatus();

    // Telemetry
    private LoggingObject csvLog;

    private FaultReporter() {

        // Print startup message
        logger.log("Reporting on FPGA version: %d.%d", Level.kRobot, RobotController.getFPGAVersion(),
                RobotController.getFPGARevision());

        // Configure and start the notifier
        logger.log("Starting reporter thread", Level.kRobot);
        thread = new Notifier(this::update);
        thread.setName("Lib5K FaultReporter");
        thread.startPeriodic(0.08);

        // Configure csv logging
        csvLog = StatusLogger.getInstance().createLoggingObject("FaultReporter", "robot_voltage", "can_usage",
                "can_tx_errors", "can_rx_errors", "3v_faults", "5v_faults", "6v_faults", "3v_enabled", "5v_enabled",
                "6v_enabled", "brownout");
    }

    public static FaultReporter getInstance() {
        if (instance == null) {
            instance = new FaultReporter();
        }

        return instance;
    }

    private void update() {

        // Track brownout states
        boolean brownout_state = RobotController.isBrownedOut();
        csvLog.setValue("brownout", brownout_state);

        if (brownout_state != lastBrownoutState && brownout_state) {
            logger.log("Robot brownout detected!", Level.kWarning);
        }

        lastBrownoutState = brownout_state;

        // Track system states
        boolean system_state = RobotController.isSysActive();

        if (system_state != lastSystemState) {
            logger.log("Robot FPGA outputs have been " + ((system_state) ? "enabled" : "disabled"));
        }

        lastSystemState = system_state;

        // Report rail statuses
        handleRailStatuses();

        // Report CAN bus status
        handleCANStatus();

        // CSVLog voltage
        double bus_voltage = RR_HAL.getSimSafeVoltage();
        csvLog.setValue("bus_voltage", bus_voltage);

    }

    /**
     * Track and report IO rail faults and events
     */
    private void handleRailStatuses() {
        // Track 3v3 state
        boolean enabled3v3 = RobotController.getEnabled3V3();
        if (last3v3Enabled != enabled3v3) {
            logger.log("3v3 Rail " + ((enabled3v3) ? "Enabled" : "Disabled"));
        }

        last3v3Enabled = enabled3v3;
        csvLog.setValue("3v_enabled", enabled3v3);

        // Check 3v3 faults
        int new3v3count = RobotController.getFaultCount3V3();
        if (new3v3count != count3v3) {
            logger.log("3v3 Rail fault detected!", Level.kWarning);

            count3v3 = new3v3count;
        }
        csvLog.setValue("3v_faults", new3v3count);

        // Track 5v state
        boolean enabled5v = RobotController.getEnabled5V();
        if (last5vEnabled != enabled5v) {
            logger.log("5v Rail " + ((enabled5v) ? "Enabled" : "Disabled"));
        }

        last5vEnabled = enabled5v;
        csvLog.setValue("5v_enabled", enabled5v);

        // Check 5v faults
        int new5vcount = RobotController.getFaultCount5V();
        if (new5vcount != count5v) {
            logger.log("5v Rail fault detected!", Level.kWarning);

            count5v = new5vcount;
        }
        csvLog.setValue("5v_faults", new5vcount);

        // Track 6v state
        boolean enabled6v = RobotController.getEnabled6V();
        if (last6vEnabled != enabled6v) {
            logger.log("6v Rail " + ((enabled6v) ? "Enabled" : "Disabled"));
        }

        last6vEnabled = enabled6v;
        csvLog.setValue("6v_enabled", enabled6v);

        // Check 6v faults
        int new6vcount = RobotController.getFaultCount6V();
        if (new6vcount != count6v) {
            logger.log("6v Rail fault detected!", Level.kWarning);

            count6v = new6vcount;
        }
        csvLog.setValue("6v_faults", new6vcount);
    }

    /**
     * Report CAN bus faults and status
     */
    private void handleCANStatus() {

        // Get current status
        CANStatus current_status = RobotController.getCANStatus();

        // Report High CAN usage
        if (current_status.percentBusUtilization > canAcceptableUsage) {
            logger.log("CAN bus utilization has passed %%%.2f", Level.kWarning, canAcceptableUsage);
        }
        csvLog.setValue("can_usage", current_status.percentBusUtilization);

        // Report CAN TX errors
        if (current_status.transmitErrorCount != lastCANStatus.transmitErrorCount) {
            logger.log("CAN bus TX error", Level.kWarning);
        }
        csvLog.setValue("can_tx_errors", current_status.transmitErrorCount);

        // Report CAN RX errors
        if (current_status.receiveErrorCount != lastCANStatus.receiveErrorCount) {
            logger.log("CAN bus RX error", Level.kWarning);
        }
        csvLog.setValue("can_rx_errors", current_status.receiveErrorCount);

        // Update last statuses
        lastCANStatus.setStatus(current_status.percentBusUtilization, current_status.busOffCount,
                current_status.txFullCount, current_status.receiveErrorCount, current_status.transmitErrorCount);

    }
}