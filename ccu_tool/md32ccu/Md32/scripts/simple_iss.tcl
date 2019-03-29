#
# Tcl srcipt for simulation runs (From Target distribution)
#

# Create ISS
set procname [lindex [::iss::processors] 0]
::iss::create $procname iss
set procdir [iss info processor_dir {} 0]
set program $::iss::tcl_script_args

puts "procdir = $procdir"
puts "program = $program"

# Load program 
iss program load $program -dwarf2 -sourcepath {.} 

# Simulate until end of main
if  { [catch { iss step -1 } msg ] } {
  puts $msg
}

# Save instruction profile in human readable form
iss profile save $program.prf  

iss close
exit

