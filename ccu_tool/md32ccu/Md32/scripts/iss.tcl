#
# Tcl script for regression simulation runs.
#

# Create ISS

lappend auto_path [file dirname [info script]]

set procname [lindex [::iss::processors] 0]
::iss::create $procname iss

set procdir [iss info processor_dir {} 0]

set program [lindex $::iss::tcl_script_args 0]

set argi 1
set nargs [llength $::iss::tcl_script_args]
set proffile ""
set proffunc ""
while { $argi < $nargs } {
  if { [lindex $::iss::tcl_script_args $argi] eq "-prof" } {
    set proffile "profile.txt"
    if { $argi + 1 < $nargs } { 
      set proffile [lindex $::iss::tcl_script_args [expr $argi + 1]]
    }
    set argi [expr $argi + 2]
  } elseif { [lindex $::iss::tcl_script_args $argi] eq "-timefn" } {
    set proffunc [lindex $::iss::tcl_script_args [expr $argi + 1]]
    set argi [expr $argi + 2]
  } else {
    puts "Unknown script argument: [lindex $::iss::tcl_script_args $argi]"
    set argi [expr $argi + 1]
  }
}


# Load program 
iss program load $program -nmlpath $procdir -dwarf2 -disassemble -sourcepath {.} 

package require timer
::timer::setup ::iss

iss breakpoint mic set 8

# Simulate until end of main
catch { iss step -1 } msg
flush stdout
flush stderr
puts $msg
catch { iss step 4 } msg
flush stdout
flush stderr
puts $msg
if { $proffunc ne "" } {
  # Write a profile and extract the func+descendants time for the named function
  iss profile save profile.func.txt -type function_statistics -entry_pc 0 -function_details 0 -call_details 0
  set prof [open profile.func.txt]
  set re0 {^\s*(?:\d+\s+){2}(?:\d+\.\d+%\s+){1}(?:\d+\s+){3}(\d+)\s+(?:\d+\.\d+%\s+){1}(?:\d+\s+){5}(\w*}
  set re1 {\w*)}
  set re "$re0$proffunc$re1"
  while { [gets $prof line] >= 0 } {
    if { [regexp $re $line dummy time name] } {
      puts "Simulation ended - timing function $name\n  time=$time"
      break
    }
  }
  close $prof
} else {
  ::timer::finish_log
}

if { $proffile ne "" } {
  iss profile save $proffile -type instruction_level
}

iss close
exit
