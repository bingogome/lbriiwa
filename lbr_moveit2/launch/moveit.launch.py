
import os
import yaml
from launch import LaunchDescription
from launch_ros.actions import Node
from ament_index_python.packages import get_package_share_directory
from launch.actions import IncludeLaunchDescription
from launch.launch_description_sources import PythonLaunchDescriptionSource

def load_file(package_name, file_path):
    package_path = get_package_share_directory(package_name)
    absolute_file_path = os.path.join(package_path, file_path)

    try:
        with open(absolute_file_path, 'r') as file:
            return file.read()
    except EnvironmentError:
        return None

def load_yaml(package_name, file_path):
    package_path = get_package_share_directory(package_name)
    absolute_file_path = os.path.join(package_path, file_path)

    try:
        with open(absolute_file_path, 'r') as file:
            return yaml.load(file)
    except EnvironmentError:
        return None


def generate_launch_description():
    moveit_cpp_yaml_file_name = get_package_share_directory('lbr_moveit2') + "/config/moveit_cpp.yaml"

    robot_description_config = load_file('lbr_bringup', 'urdf/iiwa7.urdf')
    robot_description = {'robot_description' : robot_description_config}

    robot_description_semantic_config = load_file('lbr_moveit2', 'config/iiwa7.srdf')
    robot_description_semantic = {'robot_description_semantic' : robot_description_semantic_config}

    kinematics_yaml = load_yaml('lbr_moveit2', 'config/kinematics.yaml')
    robot_description_kinematics = {'robot_description_kinematics' : kinematics_yaml }

    controllers_yaml = load_yaml('lbr_moveit2', 'config/controllers.yaml')
    # moveit_controllers = {'moveit_simple_controller_manager' : controllers_yaml }
    moveit_controllers = {'moveit_simple_controller_manager' : controllers_yaml,
                          'moveit_controller_manager': 'moveit_simple_controller_manager/MoveItSimpleControllerManager'}

    ompl_planning_pipeline_config = { 'ompl' : {
        'planning_plugin' : 'ompl_interface/OMPLPlanner',
        'request_adapters' : """default_planner_request_adapters/AddTimeOptimalParameterization default_planner_request_adapters/FixWorkspaceBounds default_planner_request_adapters/FixStartStateBounds default_planner_request_adapters/FixStartStateCollision default_planner_request_adapters/FixStartStatePathConstraints""" ,
        'start_state_max_bounds_error' : 0.1 } }
    ompl_planning_yaml = load_yaml('lbr_moveit2', 'config/ompl_planning.yaml')
    ompl_planning_pipeline_config['ompl'].update(ompl_planning_yaml)

    trajectory_execution = {'moveit_manage_controllers': True,
                        'trajectory_execution.allowed_execution_duration_scaling': 1.2,
                        'trajectory_execution.allowed_goal_duration_margin': 0.5,
                        'trajectory_execution.allowed_start_tolerance': 0.01}

    planning_scene_monitor_parameters = {"publish_planning_scene": True,
                 "publish_geometry_updates": True,
                 "publish_state_updates": True,
                 "publish_transforms_updates": True}

    state_publisher_launch_file_dir = os.path.join(get_package_share_directory('lbr_bringup'), 'launch')

    run_moveit_node = Node(name='run_moveit',
                               package='lbr_moveit2',
                               executable='run_moveit',
                               output='screen',
                               emulate_tty=True,
                               parameters=[robot_description,
                                           robot_description_semantic,
                                           kinematics_yaml,
                                           ompl_planning_pipeline_config,
                                           trajectory_execution,
                                           moveit_controllers,
                                           planning_scene_monitor_parameters])
                                           
    # RViz
    rviz_config_file = get_package_share_directory('lbr_moveit2') + "/rviz/moveit.rviz"
    rviz_node = Node(package='rviz2',
                     executable='rviz2',
                     name='rviz2',
                     output='screen',
                     arguments=['-d', rviz_config_file],
                     parameters=[robot_description])

    # Publish base link TF
    static_tf = Node(package='tf2_ros',
                     executable='static_transform_publisher',
                     name='static_transform_publisher',
                     output='screen',
                     arguments=['0.0', '0.0', '0.0', '0.0', '0.0', '0.0', 'world', 'base_iiwa'])

    # Fake joint driver in MoveIt2
    fake_joint_driver_node = Node(package='fake_joint_driver',
                                  executable='fake_joint_driver_node',
                                  parameters=[{'controller_name': 'iiwa7_controller'},
                                              os.path.join(get_package_share_directory("lbr_moveit2"), "config", "iiwa_controllers.yaml"),
                                              os.path.join(get_package_share_directory("lbr_moveit2"), "config", "start_positions.yaml"),
                                              robot_description],
                                  output='screen',
                                  )

    return LaunchDescription([ static_tf, rviz_node, run_moveit_node, fake_joint_driver_node,
     IncludeLaunchDescription(
           PythonLaunchDescriptionSource([state_publisher_launch_file_dir, '/state_publisher.launch.py']),
       ),
        ])