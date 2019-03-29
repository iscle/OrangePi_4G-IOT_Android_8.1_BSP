import sys
import re
import os

def turnOffSelfReg(projectConfigFile):
	DevRegFeatureOptionName = 'MTK_DEVREG_APP'
	SelfRegFeatureOptionName = 'MTK_CT4GREG_APP'
	CmccFeatureOptionName = 'CMCC_LIGHT_CUST_SUPPORT'
	OptrFeatureOptionName = 'OPTR_SPEC_SEG_DEF'
	OP09Cproject = 'OP09_SPEC0212_SEGC'
	matchOP09C = 'no'
	matchCMCC = 'no'
	print (projectConfigFile)
	file = open(projectConfigFile,"r")
	if not os.path.exists(projectConfigFile):
		exit(1)
	lines = file.readlines()
	file.close();

	i = 0;
	for line in lines:
		#Find value of OPTR_SPEC_SEG_DEF and CMCC_LIGHT_CUST_SUPPORT
		if line.startswith(CmccFeatureOptionName):
			actual = line[line.index('=')+1:].strip()
			print(line)
			if re.match('yes', actual, re.I):
				matchCMCC = 'yes'
		if line.startswith(OptrFeatureOptionName):
			actual = line[line.index('=')+1:].strip()
			print(line)
			if re.match(OP09Cproject, actual, re.I):
				matchOP09C = 'yes'
		i += 1
	#If OPTR_SPEC_SEG_DEF = OP09_SPEC0212_SEGC and CMCC_LIGHT_CUST_SUPPORT = yes
	#set  MTK_DEVREG_APP = no,MTK_CT4GREG_APP = no
	if re.match('yes', matchOP09C, re.I) and re.match('yes', matchCMCC, re.I):
		i = 0;
		for line in lines:
			if line.startswith(DevRegFeatureOptionName):
				actual = line[line.index('=')+1:].strip()
				print(line)
				#Reset MTK_DEVREG_APP = no
				if re.match('yes', actual, re.I):
					lines[i] = line.replace("yes", "no")
			if line.startswith(SelfRegFeatureOptionName):
				actual = line[line.index('=')+1:].strip()
				print(line)
				#Reset MTK_CT4GREG_APP = no
				if re.match('yes', actual, re.I):
					lines[i] = line.replace("yes", "no")
			i += 1
		#write data to projectConfigFile
		file = open(projectConfigFile,"w")
		file.writelines(lines)
		file.close();

def main(args):
    print(args[1]);
    turnOffSelfReg(args[1]);

if __name__ == '__main__':
    main(sys.argv)