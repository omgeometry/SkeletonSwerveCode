// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.Subsystems;

import org.littletonrobotics.junction.Logger;

import com.kauailabs.navx.frc.AHRS;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.util.HolonomicPathFollowerConfig;
import com.pathplanner.lib.util.PIDConstants;
import com.pathplanner.lib.util.ReplanningConfig;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.estimator.SwerveDrivePoseEstimator;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.TrapezoidProfile.Constraints;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StructArrayPublisher;
import edu.wpi.first.networktables.StructPublisher;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import frc.robot.sensors.Camera;

/** Represents a swerve drive style drivetrain. */
public class SwerveDrive extends SubsystemBase {

  private static SwerveDrive instance = new SwerveDrive();
  ProfiledPIDController thetaController = new ProfiledPIDController(2, 0, .1, new Constraints(360, 720));
  SwerveModuleState[] swerveModuleStates = new SwerveModuleState[4];
  private final SwerveDrivePoseEstimator poseEstimator;
  // private Camera camera = Camera.getInstance();
  public static AHRS gyro;

  private final Translation2d[] locations = {
      new Translation2d(Constants.botLength, Constants.botLength),
      new Translation2d(Constants.botLength, -Constants.botLength),
      new Translation2d(-Constants.botLength, Constants.botLength),
      new Translation2d(-Constants.botLength, -Constants.botLength)
  };

  SwerveModule[] modules = {
      new SwerveModule("frontLeft", 3, 8, 7, 0.701239),
      new SwerveModule("frontRight", 2, 6, 5, 0.707867),
      new SwerveModule("backLeft", 0, 2, 1, 0.219279),
      new SwerveModule("backRight", 1, 4, 3, 0.447409),

  };
  double odometryOffset = 0;

  private ChassisSpeeds botSpeeds = new ChassisSpeeds(0, 0, 0);

  private final SwerveDriveKinematics kinematics = new SwerveDriveKinematics(
      locations[0], locations[1], locations[2], locations[3]);

  /*
   * Here we use SwerveDrivePoseEstimator so that we can fuse odometry readings.
   * The numbers used
   * below are robot specific, and should be tuned.
   */

  public SwerveDrive() {
    NetworkTableInstance.getDefault().getTable("VisionStdDev").getEntry("VisionstdDev").setDouble(.01);
    thetaController.enableContinuousInput(-Math.PI, Math.PI);
    thetaController.setTolerance(Math.PI / 45); // 4 degrees
    gyro = new AHRS(SPI.Port.kMXP);
    gyro.reset();

    // Autobuilder for Pathplanner Goes last in constructor! TK
    AutoBuilder.configureHolonomic(
        this::getPose, // Robot pose supplier
        this::resetPose, // Method to reset odometry (will be called if your auto has a starting pose)
        this::getRobotRelativeSpeeds, // ChassisSpeeds supplier. MUST BE ROBOT RELATIVE
        this::driveRobotRelative, // Method that will drive the robot given ROBOT RELATIVE ChassisSpeeds
        new HolonomicPathFollowerConfig( // HolonomicPathFollowerConfig, this should likely live in your
                                         // Constants class
            new PIDConstants(4, 0.0, 0), // Translation PID constants
            new PIDConstants(4, 0.0, 0), // Rotation PID constants
            Constants.maxChassisSpeed, // Max module speed, in m/s
            Constants.botRadius, // Drive base radius in meters. Distance from robot center to furthest module.
            new ReplanningConfig() // Default path replanning config. See the API for the options here
        ),
        this::shouldFlipPath,
        this // Reference to this subsystem to set requirements
    );
    //std deviation taken from examples
    poseEstimator = new SwerveDrivePoseEstimator(
        kinematics,
        gyro.getRotation2d(),
        new SwerveModulePosition[] {
            modules[0].getSwerveModulePosition(),
            modules[1].getSwerveModulePosition(),
            modules[2].getSwerveModulePosition(),
            modules[3].getSwerveModulePosition()
        },
        new Pose2d(),
        VecBuilder.fill(0.1, 0.1, Units.degreesToRadians(2)),
        VecBuilder.fill(1, 1, Units.degreesToRadians(30
        )));

    Logger.recordOutput("Actual States", states);
    Logger.recordOutput("Set States", swerveModuleStates);
    Logger.recordOutput("Odometry", poseEstimator.getEstimatedPosition());
  }

  // var alliance = DriverStation.getAlliance();
  // if (alliance.isPresent() && allowPathMirroring) {
  // return alliance.get() == DriverStation.Alliance.Red;
  // }
  // return false;

  // WPILib
  StructArrayPublisher<SwerveModuleState> actualStates = NetworkTableInstance.getDefault()
      .getStructArrayTopic("Actual States", SwerveModuleState.struct).publish();
  StructArrayPublisher<SwerveModuleState> setStates = NetworkTableInstance.getDefault()
      .getStructArrayTopic("Set States", SwerveModuleState.struct).publish();
  StructPublisher<Pose2d> odometryStruct = NetworkTableInstance.getDefault()
      .getStructTopic("Odometry", Pose2d.struct).publish();
  SwerveModuleState[] states = new SwerveModuleState[4];

  public void periodic() {

    for (int i = 0; i < 4; i++) {
      states[i] = modules[i].getState();
    }
    updateOdometry();
    // actualStates.set(swerveModuleStates);
    // setStates.set(states);
    // double visionStdDev = NetworkTableInstance.getDefault().getTable("VisionStdDev").getEntry("VisionstdDev")
        // .getDouble(.02);
    // poseEstimator.setVisionMeasurementStdDevs(VecBuilder.fill(visionStdDev, visionStdDev, Units.degreesToRadians(30)));
    // poseEstimator.addVisionMeasurement(camera.getEstimatedGlobalPose(),
    // camera.getTimestamp());
    odometryStruct.set(getPose());

  }

  /**
   * Method to drive the robot using joystick info.
   *
   * @param xSpeed        Speed of the robot in the x direction (forward).
   * @param ySpeed        Speed of the robot in the y direction (sideways).
   * @param rot           Angular rate of the robot.
   * @param fieldRelative Whether the provided x and y speeds are relative to the
   *                      field.
   */
  public void drive(double xSpeed, double ySpeed, double rot, boolean fieldRelative) {
    botSpeeds = ChassisSpeeds.discretize(new ChassisSpeeds(xSpeed, ySpeed, rot), .02);
    swerveModuleStates = kinematics.toSwerveModuleStates(
        ChassisSpeeds.discretize(
            fieldRelative
                ? ChassisSpeeds.fromFieldRelativeSpeeds(xSpeed, ySpeed, rot, gyro.getRotation2d())
                : new ChassisSpeeds(xSpeed, ySpeed, rot),
            .02));
    SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, Constants.maxModuleSpeed);

    for (int i = 0; i < 4; i++) {
      modules[i].setStates(swerveModuleStates[i], false);
    }
  }

  public void driveRobotRelative(ChassisSpeeds speeds) {
    drive(speeds.vxMetersPerSecond, speeds.vyMetersPerSecond, speeds.omegaRadiansPerSecond, false);
  }

  public void driveRobotAtAngle(double xSpeed, double ySpeed, double angleRadians, boolean fieldRelative) {
    thetaController.setGoal(angleRadians);
    double angularSpeed = thetaController.calculate(getPose().getRotation().getRadians());
    drive(xSpeed, ySpeed, angularSpeed, fieldRelative);
  }

  public void driveRobotFacingSpeaker(double xSpeed, double ySpeed, boolean fieldRelative) {
    Translation2d distanceFromSpeaker;
    if (shouldFlipPath()) {
      distanceFromSpeaker = getExpectedPose().getTranslation().minus(Constants.redSpeakerTranslation);
    } else {
      distanceFromSpeaker = getExpectedPose().getTranslation().minus(Constants.blueSpeakerTranslation);
    }
    double angle = Math.atan2(distanceFromSpeaker.getY(), distanceFromSpeaker.getX());

    driveRobotAtAngle(xSpeed, ySpeed, angle, fieldRelative);
  }

  /** Updates the field relative position of the robot. */
  public void updateOdometry() {
    poseEstimator.update(
        gyro.getRotation2d(),
        new SwerveModulePosition[] {
            modules[0].getSwerveModulePosition(),
            modules[1].getSwerveModulePosition(),
            modules[2].getSwerveModulePosition(),
            modules[3].getSwerveModulePosition()
        });

    // Also apply vision measurements. We use 0.3 seconds in the past as an example
    // -- on
    // a real robot, this must be calculated based either on latency or timestamps.

  //   try {
  //     if (Camera.getInstance().getStatus()) {
  //       Optional<EstimatedRobotPose> pose = Camera.getInstance().getEstimatedGlobalPose();
  //       DistAmb reading = Camera.getInstance().getApriltagDistX();
  //       if (pose.isPresent() && reading != null  
  //       // && getPose().getTranslation().getDistance(Camera.getInstance().getEstimatedGlobalPose().get().estimatedPose.getTranslation().toTranslation2d()) < .5
  //        ) {

  //         poseEstimator.addVisionMeasurement(pose.get().estimatedPose.toPose2d(),
  //             Timer.getFPGATimestamp()-.04);
  //         // System.out.println("Target Detected");
  //       } // else {
  //         // poseEstimator.addVisionMeasurement(getPose(), Timer.getFPGATimestamp());
  //         // }
  //     }
  //   } catch (Error test) {
  //     System.err.println(test);
  //   }
 }

  public SwerveModulePosition[] getModulePositions() {
    SwerveModulePosition[] positions = new SwerveModulePosition[4];
    for (int i = 0; i < 4; i++) {
      positions[i] = modules[i].getSwerveModulePosition();
    }
    return positions;
  }

  public SwerveModuleState[] getModuleStates() {
    SwerveModuleState[] states = new SwerveModuleState[4];
    for (int i = 0; i < 4; i++) {
      states[i] = modules[i].getState();
    }
    return states;
  }

  public boolean shouldFlipPath() {
    return DriverStation.getAlliance().get().equals(Alliance.Red);
  }

  public ChassisSpeeds getRobotRelativeSpeeds() {
    return kinematics.toChassisSpeeds(getModuleStates());
  }

  public Pose2d getPose() {
    return poseEstimator.getEstimatedPosition();
  }

  public Pose2d getExpectedPose() {
    return getPose().plus(new Transform2d(
        new Translation2d(botSpeeds.vxMetersPerSecond * .05, botSpeeds.vyMetersPerSecond * .05), new Rotation2d()));
  }

  public double getExpectedDistanceFromSpeaker() {
    // .05 is a placeholder timesteo, may be changed in the future
    if (shouldFlipPath()) {
      return Constants.redSpeakerTranslation.getDistance(getExpectedPose().getTranslation());
    }
    return Constants.blueSpeakerTranslation.getDistance(getExpectedPose().getTranslation());
  }

  public double getDistanceFromSpeaker() {
    if (shouldFlipPath()) {
      return Constants.redSpeakerTranslation.getDistance(getPose().getTranslation());
    }
    return Constants.blueSpeakerTranslation.getDistance(getPose().getTranslation());
  }

  public static SwerveDrive getInstance() {
    if (instance == null) {
      instance = new SwerveDrive();
    }
    return instance;
  }

  public void setVisionStdDeviations(double deviation) {
    poseEstimator.setVisionMeasurementStdDevs(VecBuilder.fill(deviation, deviation, Units.degreesToRadians(30)));
  }

    private PIDController turnController = new PIDController(0.025, 0, 0.0025);

    public void turnToAprilTag(int ID) {
      // TODO: Potential null error unhandled here
      // turnPID.enableContinuousInput(0, 360);
      try {
        double botAngle = getPose().getRotation().getDegrees();
        double offsetAngle = Camera.getInstance().getDegToApriltag(ID);
        double setpoint = 0;
      if (botAngle - offsetAngle <= 0)
        setpoint = botAngle + offsetAngle;
      else
        setpoint = botAngle - offsetAngle;

    turnController.setSetpoint(setpoint);
    drive(0, 0, turnController.calculate(botAngle), false);
  
    } catch (Exception e) {
      System.err.println(e.getLocalizedMessage());
    }
      }

  public void resetGyro() {
    odometryOffset += gyro.getAngle();
    gyro.reset();
  }

  public double getGyroAngle() {
    return gyro.getAngle();
  }

  public void resetPose(Pose2d pose) {
    poseEstimator.resetPosition(gyro.getRotation2d(), getModulePositions(), pose);
  }

}