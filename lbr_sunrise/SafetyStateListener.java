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

import com.kuka.roboticsAPI.controllerModel.Controller;
import com.kuka.roboticsAPI.controllerModel.DispatchedEventData;
import com.kuka.roboticsAPI.controllerModel.StatePortData;
import com.kuka.roboticsAPI.controllerModel.sunrise.ISunriseControllerStateListener;
import com.kuka.roboticsAPI.controllerModel.sunrise.SunriseSafetyState;
import com.kuka.roboticsAPI.deviceModel.Device;

public class SafetyStateListener implements ISunriseControllerStateListener{
	Controller controller;
	LBR_commander lbr_commander;
	LBR_status_reader lbr_status_reader;
	
	public SafetyStateListener(Controller cont, LBR_commander lbr, LBR_status_reader lbr_status) {
		controller = cont;
		lbr_commander = lbr;
		lbr_status_reader = lbr_status;
		
	}
	public void startSafetyStateListener() {
		controller.addControllerListener(this);
	}
	@Override
	public void onFieldBusDeviceConfigurationChangeReceived(String arg0,
			DispatchedEventData arg1) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onFieldBusDeviceIdentificationRequestReceived(String arg0,
			DispatchedEventData arg1) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onIsReadyToMoveChanged(Device arg0, boolean arg1) {
		System.out.println("READY TO MOVE! "+ arg1 + " "+ arg0);
		
	}
	@Override
	public void onShutdown(Controller arg0) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onStatePortChangeReceived(Controller arg0, StatePortData arg1) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onConnectionLost(Controller arg0) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void onSafetyStateChanged(Device device, SunriseSafetyState safetyState) {
		if(safetyState.getSafetyStopSignal()==SunriseSafetyState.SafetyStopType.STOP1){
			System.out.println("EMERGENCY STOP IN LISTENER:");
			Node.setEmergencyStop(true);


		}else if(safetyState.getSafetyStopSignal()==SunriseSafetyState.SafetyStopType.NOSTOP) {
			System.out.println("No longer emergency stop in listener");
			Node.setEmergencyStop(false);

		}		
	}
}

