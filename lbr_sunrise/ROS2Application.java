// Modified by Joshua Liu, JHU on Nov, 2020
// Copyright 2019 Nina Marie Wahl og Charlotte Heggem.
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

// Configuration
import javax.inject.Inject;
import javax.inject.Named;

// Implemented classes
import ros2API.LBR_commander;
import ros2API.LBR_sensor_reader;
import ros2API.LBR_status_reader;
import ros2API.SafetyStateListener;

import com.kuka.roboticsAPI.applicationModel.RoboticsAPIApplication;
import com.kuka.roboticsAPI.controllerModel.Controller;
import com.kuka.roboticsAPI.deviceModel.LBR;
import static com.kuka.roboticsAPI.motionModel.BasicMotions.ptp;
import static com.kuka.roboticsAPI.motionModel.BasicMotions.ptpHome;

// AUT MODE: 3s, T1/T2/CRR: 2s
public class ROS2Application extends RoboticsAPIApplication{
	
	// Runtime Variables
	private volatile boolean AppRunning;
	SafetyStateListener safetylistener;
	
	// Declare LBR
	@Inject
	@Named("LBR_iiwa_7_R800_1")
	public LBR lbr;
	public Controller controller;
	
	//@Inject
	//@Named("name of tool")
	//public Tool tool;
	
	// Define UDP ports
	int LBR_command_port = 30001;
	int LBR_status_port = 30002;
	int LBR_sensor_port = 30003;
	
	// Connection types
	String TCPConnection = "TCP";
	String UDPConnection = "UDP";

	// Implemented node classes
	LBR_commander lbr_commander;
	LBR_status_reader lbr_status_reader;
	LBR_sensor_reader lbr_sensor_reader;


	public void initialize() {
		System.out.println("Initializing Robotics API Application");

		// Configure robot;
		controller = getController("KUKA_Sunrise_Cabinet_1");	
		lbr = getContext().getDeviceFromType(LBR.class);

		lbr.moveAsync(ptpHome().setJointVelocityRel(0.5));
//        lbr.move(ptp(getApplicationData().getFrame("/Start")).setJointVelocityRel(0.5));

		// Create nodes for communication
		lbr_commander = new LBR_commander(LBR_command_port, lbr, TCPConnection, getApplicationData().getFrame("/Start"));

		
		// SafetyStateListener
		safetylistener = new SafetyStateListener(controller, lbr_commander, lbr_status_reader);
		safetylistener.startSafetyStateListener();
		
		
		// Check if a commander node is active
		long startTime = System.currentTimeMillis();
		int shutDownAfterMs = 10000; 
		while(!AppRunning) {
			if(lbr_commander.isSocketConnected()){
					AppRunning = true;
					System.out.println("Application ready to run!");	
					break;
			}else if((System.currentTimeMillis() - startTime) > shutDownAfterMs){
				System.out.println("Could not connect to a command node after " + shutDownAfterMs/1000 + "s. Shutting down.");	
				shutdown_application();
				break;
			}				
		}
		// Establish remaining nodes
		if(AppRunning){
			lbr_status_reader = new LBR_status_reader(LBR_status_port, lbr, TCPConnection);
			lbr_sensor_reader = new LBR_sensor_reader(LBR_sensor_port, lbr, TCPConnection);
		}
	}
	

	public void shutdown_application(){
		System.out.println("----- Shutting down Application -----");

		lbr_commander.close();

		try{
		lbr_status_reader.close();
		lbr_sensor_reader.close();
		}catch(Exception e){
			System.out.println("Could not close LBR status and sensor nodes");
		}
    	System.out.println("Application terminated");
    	    	
    	try{
    		dispose();

    	}catch(Exception e){
    		System.out.println("Application could not be terminated cleanly: " + e);
    	}
    	}
	
	public void run() {
		System.out.println("Running ROS2 API!");
		
		// Start all connected nodes
		lbr_commander.setPriority(Thread.MAX_PRIORITY);
		if(!(lbr_commander ==null)){
			if(lbr_commander.isSocketConnected()) {
				lbr_commander.start();
			}
		}
		if(!(lbr_status_reader ==null)){
			if(lbr_status_reader.isSocketConnected()) {
				lbr_status_reader.start();
			}
		}
		if(!(lbr_sensor_reader ==null)){
			if(lbr_sensor_reader.isSocketConnected()) {
				lbr_sensor_reader.start();
			}
		}
		while(AppRunning)
		{    
			AppRunning = (!lbr_commander.getShutdown());

		}
		
		System.out.println("Shutdown message received in main application");
		shutdown_application();
	}

	
	public static void main(String[] args){
		ROS2Application app = new ROS2Application();
		app.runApplication();
	}
	
}
