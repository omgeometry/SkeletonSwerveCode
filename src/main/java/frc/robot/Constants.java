
package frc.robot;

import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.util.Units;
//Measurements in meters
public interface Constants {
    public static final double intakeVoltage = 7.5;
    public static final double shooterVoltage = 9.6;
    public static final double gearRatio = 6.12;
    public static final int detectThreshold = 1000;
    public static final double botMass = 24.4;
    public static final double wheelDiameter = .1016;//.1016;
    public static final double maxShootingDistance = 3.768725;
    public static final double botLength = Units.inchesToMeters(29);

    // In meters per second, determined from the free speed of the bot via
    // SwerveDriveSpecialties
    public static final double maxChassisSpeed = 5.05968; // * 1.4044;
    public static final double maxModuleSpeed = maxChassisSpeed / wheelDiameter / Math.PI;
    public static final double maxTurnSpeed = Double.MAX_VALUE; // These are basically infinite for our purposes
    public static final double maxAcceleration = 4000;
    public static final double botRadius = Math.hypot(botLength, botLength);
    // Max Speed divided by the circumference a circle determined by the distance of
    // the module from the center, divided by 2 pi to convert to radians
    public static final double maxChassisTurnSpeed = maxChassisSpeed / botRadius;
    public static final double encoderRotationToMeters = 2 * Math.PI * ((wheelDiameter / 2) / gearRatio) / 42;

    public static final Translation2d blueSpeakerTranslation = new Translation2d(0, 216 * .0254);
    public static final Translation2d redSpeakerTranslation = new Translation2d(16.4846, 216 * .0254);

    public static final double topClimberPosition = 27.87;

    public static final double maxAmbiguity = 0.1; 
}
