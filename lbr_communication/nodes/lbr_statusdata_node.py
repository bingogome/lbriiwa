#!/usr/bin/env python3

# Copyright 2019 Nina Marie Wahl and Charlotte Heggem.
# Copyright 2019 Norwegian University of Science and Technology.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import _thread as thread
import sys
import math
import rclpy
from rclpy.node import Node
from lbr_msgs.msg import LbrStatusdata
from builtin_interfaces.msg import Time
from rclpy.qos import qos_profile_sensor_data
from script.tcpSocket import TCPSocket
from script.udpSocket import UDPSocket
from std_msgs.msg import Bool

from rclpy.utilities import remove_ros_args
import argparse

def cl_red(msge): return '\033[31m' + msge + '\033[0m'



class LbrStatusNode(Node):
    def __init__(self,connection_type,robot):
        super().__init__('lbr_statusdata_node')
        self.name='lbr_statusdata_node'
        self.last_status_timestamp = 0
        self.path_finished = False
        self.declare_parameter('port')
        port = int(self.get_parameter('port').value)
        if robot == 'LBR':
            self.declare_parameter('LBR/ip')
            ip = str(self.get_parameter('LBR/ip').value)
        else:
            ip = None


        if connection_type == 'TCP':
            self.soc = TCPSocket(ip,port,self.name)
        elif connection_type == 'UDP':
            self.soc=UDPSocket(ip,port,self.name)
        else:
            self.soc=None


        # Make Publisher for statusdata
        self.pub_lbr_statusdata = self.create_publisher(LbrStatusdata, 'lbr_statusdata', 10)

        while not self.soc.isconnected:
            pass
        self.get_logger().info('Node is ready')

        while rclpy.ok() and self.soc.isconnected:
            self.status_callback(self.pub_lbr_statusdata, self.soc.lbr_statusdata)



    def status_callback(self,status_publisher, data):
        if data != None:
            msg = LbrStatusdata()
            msg.header.stamp = self.get_clock().now().to_msg()
            status_elements = data[1].split(",")
            if (status_elements[1] != self.last_status_timestamp):
                self.last_status_timestamp = status_elements[1]
                for i in range(2, len(status_elements)):
                    split = status_elements[i].split(":")
                    if (split[0] == "ReadyToMove"):
                        if (split[1] == "true"):
                            msg.ready_to_move = True
                        else:
                            msg.ready_to_move = False
                    elif (split[0] == "isLBRmoving"):
                        if (split[1] == "true"):
                            msg.is_lbr_moving = True
                            self.soc.is_lbr_moving = True
                        else:
                            msg.is_lbr_moving = False
                            self.soc.is_lbr_moving = False
                    elif (split[0] == "PathFinished"):
                        if (split[1] == "true"):
                            msg.path_finished = True
                        else:
                            msg.path_finished = False
                    elif (split[0] == "LBRsafetyStop"):
                        if (split[1] == "true"):
                            msg.lbr_safetystop = True
                        else:
                            msg.lbr_safetystop = False
                status_publisher.publish(msg)


def main(argv=sys.argv[1:]):
    parser = argparse.ArgumentParser(formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('-c', '--connection')
    parser.add_argument('-ro', '--robot')
    args = parser.parse_args(remove_ros_args(args=argv))

    rclpy.init(args=argv)
    lbr_statusdata_node = LbrStatusNode(args.connection,args.robot)

    rclpy.spin(lbr_statusdata_node)


    try:
        lbr_statusdata_node.destroy_node()
        rclpy.shutdown()
    except:
        print(cl_red('Error: ') + "rclpy shutdown failed")


if __name__ == '__main__':
    main()
