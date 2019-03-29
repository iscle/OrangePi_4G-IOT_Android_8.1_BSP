import xml.etree.ElementTree as ET
import tempfile

DEVICE_PATH = '/vendor/etc/powerhint.xml'


def restart_power_hal(target):
  """Kill power HAL service so it can pick up new values in powerhint.xml."""
  target.execute('pkill -f android\.hardware\.power')


def set_touch_param(target, opcode, new_val):
  """Set a new value for the touch hint parameter with the specified opcode."""
  hinttype = '0x1A00'

  # Get current powerhint.xml file
  with tempfile.NamedTemporaryFile() as tmp:
    target.pull(DEVICE_PATH, tmp.name)

    # Replace current parameter value
    tree = ET.parse(tmp.name)
    xpath = './Hint[@type="{}"]/Resource[@opcode="{}"]'.format(hinttype, opcode)
    tree.findall(xpath)[0].set('value', '{:#x}'.format(new_val))

    # Write new powerhint.xml file to device
    tree.write(tmp.name)
    target.push(tmp.name, DEVICE_PATH)

  # Restart power HAL to pick up new value
  restart_power_hal(target)


def set_touch_boost(target, boost=50):
  """Change the top-app schedtune.boost value to use after touch events."""
  opcode = '0x42C18000'
  if boost < 0:
    boost = 100-boost
  set_touch_param(target, opcode, boost)


def set_touch_min_freq(target, cluster, freq=1100):
  """Change the CPU cluster min frequency (in Mhz) to use after touch events."""
  opcode = '0x40800000' if cluster == 'big' else '0x40800100'
  set_touch_param(target, opcode, freq)


def set_touch_cpubw_hysteresis(target, enable=False):
  """Set whether to leave CPUBW hysteresis enabled after touch events."""
  opcode = '0x4180C000'
  enable_num = 1 if enable else 0
  set_touch_param(target, opcode, enable_num)


def set_touch_cpubw_min_freq(target, freq=51):
  """Set CPUBW min freq used after touch events. See mapping in msm8998.dtsi."""
  opcode = '0x41800000'
  set_touch_param(target, opcode, freq)


def restore_defaults(target, powerhint_host_path):
  """Restore default power hint settings using a powerhint.xml file from the host."""
  target.push(powerhint_host_path, DEVICE_PATH)

  restart_power_hal(target)


def disable_launch_hint(target):
  """Turn off all launch hint tweaks."""
  hinttype = '0x1B00'

  with tempfile.NamedTemporaryFile() as tmp:
    target.pull(DEVICE_PATH, tmp.name)

    tree = ET.parse(tmp.name)
    xpath = './Hint[@type="{}"]'.format(hinttype)
    launch_hints = tree.findall(xpath)[0]
    for child in launch_hints.findall('./Resource'):
      launch_hints.remove(child)

    tree.write(tmp.name)
    target.push(tmp.name, DEVICE_PATH)

  restart_power_hal(target)
