// Copyright 2019 Norwegian University of Science and Technology.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package ros2API;

// Implemented classes
import ros2API.PTPpoint;


// RoboticsAPI
import com.kuka.roboticsAPI.deviceModel.JointPosition;
import com.kuka.roboticsAPI.deviceModel.LBR;
import com.kuka.roboticsAPI.executionModel.ExecutionState;
import com.kuka.roboticsAPI.executionModel.ICommandContainer;
import com.kuka.roboticsAPI.executionModel.IExecutionContainer;
import com.kuka.roboticsAPI.geometricModel.AbstractFrame;
import com.kuka.roboticsAPI.motionModel.IMotion;
import com.kuka.roboticsAPI.motionModel.IMotionContainer;
import com.kuka.roboticsAPI.motionModel.IMotionContainerListener;
import com.kuka.roboticsAPI.motionModel.SplineJP;
import com.kuka.roboticsAPI.motionModel.SplineMotionJP;
import static com.kuka.roboticsAPI.motionModel.BasicMotions.ptp;

// Java util
import java.util.ArrayList;
import java.util.List;


public class LBR_commander extends Node{

	// Robot Specific
	LBR lbr;
	
	// Motion: LBR
	private JointPosition CommandedjointPos;
	IMotionContainer currentmotion;
	private final double defaultVelocity = 0.2;
	AbstractFrame drivePos;
	ICommandContainer LBR_currentMotion;
	private int jointCount;
	private static final double zero = Math.pow(10,-40);

	
	// Spline Motion
	double[] poses;
	double[] velocities;
	double[] accelerations;
	private List<SplineMotionJP<?>> splineSegments = new ArrayList<SplineMotionJP<?>>();


	public LBR_commander(int port, LBR robot, String ConnectionType, AbstractFrame drivepos) {
		super(port, ConnectionType, "LBR commander");
		
		this.lbr = robot;
		this.drivePos = drivepos;
		
		jointCount=lbr.getJointCount();
		poses = new double[jointCount];
		velocities = new double[jointCount];
		accelerations = new double[jointCount];
		
		if (!(isSocketConnected())) {
			System.out.println("Starting thread to connect LBR command node....");
			Thread monitorLBRCommandConnections = new MonitorLBRCommandConnectionsThread();
			monitorLBRCommandConnections.start();
			}else {
				setisLBRConnected(true);
			}

	}
	
	@Override
	public void run() {
		Thread emergencystopthread = new MonitorEmergencyStopThread();
		emergencystopthread.start();
		
		CommandedjointPos = lbr.getCurrentJointPosition();
		
		while(isNodeRunning())
		{   
			String Commandstr = socket.receive_message(); 
	    	String []splt = Commandstr.split(" ");
	    	if(!getShutdown()&& !closed){
		    	if ((splt[0]).equals("shutdown")){
		    		System.out.println("LBR RECEIVED SHUTDOWN");
		    		setShutdown(true);
		    		break;
					}
				if ((splt[0]).equals("setLBRmotion")){
					JointLBRMotion(Commandstr);
					}
				if ((splt[0]).equals("pathPointLBR")){
					addPointToSegment(Commandstr);
					}
		}}
    }

	private void addPointToSegment(String commandstr){
		String []lineSplt = commandstr.split(">");
		String pointType = lineSplt[1];
		
		// Skip start point as velocity and acceleration is 0
		if(pointType.equals("StartPoint")){
			splineSegments.clear();
			System.out.println("Startpoint received");
			setisPathFinished(false);
		}else{
			// Read message
			for(int i = 0; i < jointCount ; ++i){
				poses[i] = Double.parseDouble(lineSplt[2].split(" ")[i]);
				if(pointType.equals("WayPoint")){
					velocities[i]= scaleValueToOne(Math.abs(Double.parseDouble(lineSplt[3].split(" ")[i])));
					double accel = scaleValueToOne(Math.abs(Double.parseDouble(lineSplt[4].split(" ")[i])));
					if(accel==0.0){accel = zero;}
					accelerations[i] = accel;
				}
			}
			PTPpoint ptp = new PTPpoint(pointType, new JointPosition(poses), velocities, accelerations);
			splineSegments.add(ptp.getPTP());
			
			if(pointType.equals("EndPoint")){
				System.out.println("endpoint received");
				followPath();
			}
		}
	}
	private double scaleValueToOne(double value){
		if(value>1){
			value=1.0;
			}
		return value;
	}

	private void followPath(){
		if( !(getEmergencyStop()) && !splineSegments.isEmpty() && isNodeRunning()){
			SplineJP spline = new SplineJP(splineSegments.toArray(new SplineMotionJP<?>[splineSegments.size()]));
			currentmotion = lbr.moveAsync(spline, new SplineMotionListener());
			}
	}
	
	private void JointLBRMotion(String commandstr) {
		if(isNodeRunning() && !getEmergencyStop()){
			String []lineSplt = commandstr.split(" ");
			int jointIndex = Character.getNumericValue(lineSplt[1].charAt(1)) - 1 ;
			double direction = Double.parseDouble(lineSplt[2]);
			double jointAngle;
				if(jointIndex==-1){ // A10: Stop all
					if(getisLBRMoving()){
						currentmotion.cancel();
					}
					setisLBRMoving(false);
					CommandedjointPos.set(lbr.getCurrentJointPosition());
				}else if(jointIndex == 8){
				 // A9: Move to driveposition
					if(getisLBRMoving()){
						currentmotion.cancel();
					}
				    lbr.move(ptp(drivePos).setJointVelocityRel(defaultVelocity));
					setisLBRMoving(false);
				    CommandedjointPos.set(lbr.getCurrentJointPosition());
				}else{
					jointAngle = setJointAngle(jointIndex, direction);
					if(!(CommandedjointPos.get(jointIndex)==jointAngle)){
						CommandedjointPos.set(jointIndex, jointAngle);
						if(getisLBRMoving()){
							currentmotion.cancel();
						}
						currentmotion = lbr.moveAsync(ptp(CommandedjointPos).setJointVelocityRel(defaultVelocity));
						setisLBRMoving(true);
					}
				}	
		}
	}
	private double setJointAngle(int jointIndex, double direction) {
		double jointAngle = lbr.getCurrentJointPosition().get(jointIndex);
		if(direction==-1){
			jointAngle = Math.ceil(lbr.getJointLimits().getMinJointPosition().get(jointIndex)*100)/100;
		}else if(direction==1){
			jointAngle = Math.floor(lbr.getJointLimits().getMaxJointPosition().get(jointIndex)*100)/100;
		}
		return jointAngle;
	}
	
	public class MonitorEmergencyStopThread extends Thread {
		public void run(){
			while(isNodeRunning()) {
				if (getEmergencyStop()){
						if (getisLBRMoving()){
							System.out.println("Lbr emergencythread");
							if(!(currentmotion==null)){
								currentmotion.cancel();
							}
							CommandedjointPos.set(lbr.getCurrentJointPosition());
						}
						setisLBRMoving(false);
						}
				}
			}
		}
	
	public class MonitorLBRCommandConnectionsThread extends Thread {
		int timeout = 3000;
		public void run(){
			while(!(isSocketConnected()) && (!(closed))) {
				createSocket();
				if (isSocketConnected()){
					setisLBRConnected(true);
					break;
				}
				try {
					Thread.sleep(timeout);
				} catch (InterruptedException e) {
					System.out.println("Waiting for connection to LBR commander node ..");
				}
			}
			if(!closed){
				System.out.println("Connection with LBR Command Node OK!");
				runmainthread();
				}	
		}
	}
	
	public class SplineMotionListener implements IMotionContainerListener {
		public SplineMotionListener() { 
		  }
		@Override
		public void onStateChanged(IExecutionContainer arg0, ExecutionState arg1) {
		}

		@Override
		public void containerFinished(IMotionContainer arg0) {
			System.out.println("Container finished!");
			setisLBRMoving(false);
			setisPathFinished(true);
			splineSegments.clear();
		}

		@Override
		public void motionFinished(IMotion arg0) {
		}

		@Override
		public void motionStarted(IMotion arg0) {
			setisLBRMoving(true);
		}
	}
	
	@Override
	public void close(){
		closed = true;
		try{
		CommandedjointPos.set(lbr.getCurrentJointPosition());
		}catch(Exception e){
			System.out.println("No motion to end!");
		}

		if(getisLBRMoving()){
			try{
			currentmotion.cancel();
			}catch(Exception e){
				System.out.println("LBR could not stop motion: " +e);
			}
		}
		try{
			this.socket.close();
		}catch(Exception e){
				System.out.println("Could not close LBR commander connection: " +e);
			}
		System.out.println("LBR command closed!");

	}

	
}