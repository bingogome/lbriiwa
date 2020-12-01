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


import com.kuka.roboticsAPI.deviceModel.LBR;


public class LBR_sensor_reader extends Node{
	
	// Robot
	LBR lbr;
	
	// LBR sensor
	private double[] JointPosition;
	private double[] MeasuredTorque;
	
	// Socket
	int port;
	String ConnectionType;
	private long last_sendtime = System.currentTimeMillis();

	
	
	public LBR_sensor_reader(int port, LBR robot, String ConnectionType) {
		super(port, ConnectionType, "LBR sensor reader");
		this.lbr = robot;
		
		if(!(isSocketConnected())){
			Thread monitorLBRsensorConnections = new MonitorSensorConnectionThread();
			monitorLBRsensorConnections.start();
		}

	}
	
		
	public class MonitorSensorConnectionThread extends Thread {
		public void run(){
			while(!(isSocketConnected()) && (!(closed))) {

				createSocket();
				if (isSocketConnected()){
					break;
				}
				try {
					Thread.sleep(connection_timeout);
				} catch (InterruptedException e) {
					System.out.println("Waiting for connection to LBR commander node ..");
				}
			}
			if(!closed){
				System.out.println("Connection with KMP Command Node OK!");
				runmainthread();
				}	
		}
	}

	@Override
	public void run() {
		while(isNodeRunning())
		{	
			
			if(!isSocketConnected() || (closed)){
				break;
			}
			if(System.currentTimeMillis()-last_sendtime>30){
				updateMeasuredTorque();
				updateJointPosition();
				sendStatus();
			}
		}
	}

	private void updateJointPosition() {
		try{
		JointPosition = lbr.getCurrentJointPosition().getInternalArray();
		}catch(Exception e){}
	}

	private void updateMeasuredTorque() {
		try{
		MeasuredTorque = lbr.getMeasuredTorque().getTorqueValues();
		}catch(Exception e){}
	}

	private String generateSensorString() {
		return 	">lbr_sensordata ,"  + System.nanoTime() +  
				",JointPosition:" + JointPosition[0]+"," + JointPosition[1]+"," + JointPosition[2]+"," + JointPosition[3]+"," + JointPosition[4]+"," + JointPosition[5]+"," + JointPosition[6]+
				",MeasuredTorque:" + MeasuredTorque[0] +","+ MeasuredTorque[1] +","+MeasuredTorque[2] +","+MeasuredTorque[3] +","+MeasuredTorque[4] +","+MeasuredTorque[5] +","+MeasuredTorque[6] ;
	}
	
	public void sendStatus() {
		String sensorString = generateSensorString();
		last_sendtime = System.currentTimeMillis();
		if(isNodeRunning()){
			try{
				this.socket.send_message(sensorString);
				if(closed){
					System.out.println("LBR sensor sender selv om han ikke faar lov");
				}
			}catch(Exception e){
				System.out.println("Could not send LBR sensormessage to ROS: " + e);
			}
		}
	}
	
	@Override
	public void close() {
		closed = true;
		try{
			this.socket.close();
		}catch(Exception e){
				System.out.println("Could not close LBR sensor connection: " +e);
			}
		System.out.println("LBR sensor closed!");

	}
	
}
