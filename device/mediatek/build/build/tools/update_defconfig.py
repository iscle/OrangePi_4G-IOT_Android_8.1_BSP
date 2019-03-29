#!/usr/bin/env python
# 
# Copyright Statement:
# --------------------
# This software is protected by Copyright and the information contained
# herein is confidential. The software may not be copied and the information
# contained herein may not be used or disclosed except with the written
# permission of MediaTek Inc. (C) 2010
# 
# BY OPENING THIS FILE, BUYER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
# THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
# RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO BUYER ON
# AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
# NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
# SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
# SUPPLIED WITH THE MEDIATEK SOFTWARE, AND BUYER AGREES TO LOOK ONLY TO SUCH
# THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. MEDIATEK SHALL ALSO
# NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE RELEASES MADE TO BUYER'S
# SPECIFICATION OR TO CONFORM TO A PARTICULAR STANDARD OR OPEN FORUM.
# 
# BUYER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND CUMULATIVE
# LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
# AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
# OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY BUYER TO
# MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
# 
# THE TRANSACTION CONTEMPLATED HEREUNDER SHALL BE CONSTRUED IN ACCORDANCE
# WITH THE LAWS OF THE STATE OF CALIFORNIA, USA, EXCLUDING ITS CONFLICT OF
# LAWS PRINCIPLES.  ANY DISPUTES, CONTROVERSIES OR CLAIMS ARISING THEREOF AND
# RELATED THERETO SHALL BE SETTLED BY ARBITRATION IN SAN FRANCISCO, CA, UNDER
# THE RULES OF THE INTERNATIONAL CHAMBER OF COMMERCE (ICC).
# 

import os
import sys
import stat
import shutil
import re


def get_mtk_platform(file_name):
	file_handle = open(file_name, 'r')
	pattern_arch = re.compile('^CONFIG_(ARCH|MACH)_(MT\d\d\d\d[A-Z]?)=y$')
	mtk_platform = ''
	for line in file_handle.readlines():
		result = pattern_arch.match(line)
		if result:
			if mtk_platform == '':
				mtk_platform = result.group(2)
			else:
				print 'Warning: duplicated CONFIG_ARCH_MTxxxx is found from ' + file_name
				mtk_platform = mtk_platform + ' ' + result.group(2)
		elif line.startswith('CONFIG_ARCH_ELBRUS') or line.startswith('CONFIG_MACH_ELBRUS'):
			if mtk_platform == '':
				mtk_platform = 'ELBRUS'
	file_handle.close()
	return mtk_platform


def get_kernel_root(file_name):
	fpath = os.path.abspath(file_name)
	fname = ''
	kernel_root = ''
	kernel_arch = ''
	while True:
		(fpath, fname) = os.path.split(fpath)
		if fpath == '':
			break
		if fpath == '/':
			break
		if fname == 'arch':
			kernel_root = fpath
			break
		kernel_arch = fname
	return (kernel_root, kernel_arch)


def get_kernel_out(kernel_root):
	kernel_out = 'out_%d' % os.getpid()
	kernel_out_full = os.path.join(kernel_root, kernel_out)
	kernel_config = os.path.join(kernel_root, kernel_out, '.config')
	return (kernel_out, kernel_out_full, kernel_config)


def exec_oldconfig(kernel_root, kernel_arch, file_name, action_name):
	(kernel_out, kernel_out_full, kernel_config) = get_kernel_out(kernel_root)
	if not os.path.abspath(file_name) == kernel_config:
		if os.path.exists(kernel_out_full):
			shutil.rmtree(kernel_out_full)
	if not os.path.exists(kernel_out_full):
		os.mkdir(kernel_out_full)
	if not os.path.abspath(file_name) == kernel_config:
		shutil.copyfile(file_name, kernel_config)
	if action_name == 'savedefconfig':
		kernel_cmd = 'make --no-print-directory --silent -C %s ARCH=%s O=%s %s' % (kernel_root, kernel_arch, kernel_out, 'olddefconfig')
		print 'Command: ' + kernel_cmd
		res_cmd = os.system(kernel_cmd)
	kernel_cmd = 'make --no-print-directory --silent -C %s ARCH=%s O=%s %s' % (kernel_root, kernel_arch, kernel_out, action_name)
	print 'Command: ' + kernel_cmd
	res_cmd = os.system(kernel_cmd)
	if os.path.abspath(file_name) == kernel_config:
		return res_cmd
	if res_cmd == 0:
		if action_name == 'savedefconfig':
			save_config = os.path.join(kernel_out_full, 'defconfig')
		else:
			save_config = kernel_config
		kernel_cmp = 'diff %s %s' % (save_config, file_name)
		res_cmp = os.system(kernel_cmp)
		if not res_cmp == 0:
			print 'Write: ' + file_name + ' from ' + save_config
			os.chmod(file_name, stat.S_IRUSR|stat.S_IWUSR|stat.S_IRGRP|stat.S_IROTH)
			shutil.copyfile(save_config, file_name)
		else:
			print 'Skip: ' + file_name + ' is not changed'
	else:
		print 'Error: fail to run ' + action_name
	shutil.rmtree(kernel_out_full)
	return res_cmd


def modify_exec_oldconfig(kernel_root, kernel_arch, file_name, option_list, action_name):
	(kernel_out, kernel_out_full, kernel_config) = get_kernel_out(kernel_root)
	#print 'kernel_root = ' + kernel_root
	#print 'kernel_config = ' + kernel_config
	if os.path.exists(kernel_out_full):
		shutil.rmtree(kernel_out_full)
	os.mkdir(kernel_out_full)
	shutil.copyfile(file_name, kernel_config)
	diff_number = modify_defconfig(kernel_config, option_list, 'update')
	res_cmd = exec_oldconfig(kernel_root, kernel_arch, kernel_config, action_name)
	if res_cmd == 0:
		if action_name == 'savedefconfig':
			diff_number = 0
		else:
			diff_number = modify_defconfig(kernel_config, option_list, 'check')
		if diff_number == 0:
			if action_name == 'savedefconfig':
				save_config = os.path.join(kernel_out_full, 'defconfig')
			else:
				save_config = kernel_config
			kernel_cmp = 'diff %s %s' % (save_config, file_name)
			res_cmp = os.system(kernel_cmp)
			if not res_cmp == 0:
				print 'Write: ' + file_name + ' from ' + save_config
				try:
					os.chmod(file_name, stat.S_IRUSR|stat.S_IWUSR|stat.S_IRGRP|stat.S_IROTH)
				except Exception:
					pass
				shutil.copyfile(save_config, file_name)
			else:
				print 'Skip: ' + file_name + ' is not changed'
		else:
			print 'Error: fail to update ' + file_name
	else:
		print 'Error: fail to run ' + action_name
	shutil.rmtree(kernel_out_full)
	return diff_number


def modify_defconfig(file_name, option_list, action_name):
	pattern_option_on = re.compile('^(CONFIG_\S+)\s*=\s*(.+)$')
	pattern_option_off = re.compile('# (CONFIG_\S+) is not set')
	option_dict = {}
	option_count = {}
	for option_item in option_list:
		option_name = ''
		result = pattern_option_on.match(option_item)
		if result:
			option_name = result.group(1)
		else:
			result = pattern_option_off.match(option_item)
			if result:
				option_name = result.group(1)
			else:
				print 'Warning: ignore \"' + option_item + '\" in ' + file_name
		if not option_name == '':
			option_dict[option_name] = option_item
			option_count[option_name] = 0
	file_handle = open(file_name, 'r')
	file_text = []
	diff_text = []
	line_number = 0
	diff_number = 0
	for line in file_handle.readlines():
		line_strip = line.strip()
		line_number = line_number + 1
		option_name = ''
		result = pattern_option_on.match(line)
		if result:
			option_name = result.group(1)
		else:
			result = pattern_option_off.match(line)
			if result:
				option_name = result.group(1)
		if (option_name != '') and (option_dict.has_key(option_name)):
			#print 'found ' + option_name + ' at line', line_number
			option_count[option_name] = 1
			if line_strip == option_dict[option_name]:
				# same
				file_text.append(line_strip)
			else:
				# replace
				if action_name == 'check':
					diff_text.append('Error: fail to set ' + option_dict[option_name])
				else:
					diff_text.append('@@ -%d,1 +%d,1' % (line_number, line_number))
					diff_text.append('-' + line_strip)
					diff_text.append('+' + option_dict[option_name])
				file_text.append(option_dict[option_name])
				diff_number = diff_number + 1
		else:
			# not in scope
			file_text.append(line_strip)
	file_handle.close();
	flag_append = 0;
	for option_name in option_dict.keys():
		if option_count[option_name] == 0:
			# append
			line_number = line_number + 1
			if flag_append == 0:
				if not action_name == 'check':
					diff_text.append('@@%d' % line_number)
				flag_append = 1
			if action_name == 'check':
				diff_text.append('Error: fail to set ' + option_dict[option_name])
			else:
				diff_text.append('+' + option_dict[option_name])
			file_text.append(option_dict[option_name])
			diff_number = diff_number + 1
	if diff_number > 0:
		for line in diff_text:
			print line
		if action_name == 'update':
			# write to origin defconfig
			print 'Write: ' + file_name
			try:
				os.chmod(file_name, stat.S_IRUSR|stat.S_IWUSR|stat.S_IRGRP|stat.S_IROTH)
			except Exception:
				pass
			file_handle = open(file_name, 'w')
			for line in file_text:
				file_handle.write(line + '\n')
			file_handle.close()
	else:
		if action_name == 'update':
			print 'Skip: ' + file_name + ' is not changed'
	return diff_number


def help(argv):
	print 'Usage:'
	print argv[0] + ' config_dir project_name[.mode] option_file action_name'
	print ' config_dir:   folder containing defconfig'
	print ' project_name: pattern to filter defconfig'
	print '               ALL       - all defconfig files'
	print '               COMMON    - all MTK defconfig files'
	print '               MTxxxx    - MTxxxx platform defconfig files'
	print '               project   - project_defconfig and project_debug_defconfig'
	print ' mode:         flag to filter defconfig with/without _debug'
	print '               debug     - with _debug, such as project_name_debug_defconfig'
	print '               user      - without _debug, such as project_name_defconfig'
	print '               for default, it is not defined, which means both of debug and user'
	print ' option_file:  sample makefile containing option modified'
	print '               xxx.mk    - use sample makefile'
	print '               KCONFIG   - use nothing but run *config from kernel build system'
	print ' action_name:  write to file or just show'
	print '               update        - write to file directly'
	print '               show          - just show, dry run'
	print '               savedefconfig - use savedefconfig from kernel build system, recommended'
	print '               oldconfig     - use oldconfig from kernel build system'
	print ''
	print 'Example:'
	print argv[0] + ' kernel-3.4/arch/arm/configs COMMON sample.mk update'
	print argv[0] + ' kernel-3.4/arch/arm/configs MT6572.user KCONFIG oldconfig'
	print ''
	sys.exit(2)


def main(argv):
	if len(argv) != 5:
		help(argv)
	#
	config_dir = argv[1]
	project_list = argv[2]
	option_file = argv[3]
	action_name = argv[4]
	result = 0
	defconfig_list = []
	pattern_platform = re.compile('^MT\d\d\d\d[A-Z]?$')
	pattern_config = re.compile('\w*config$')
	pattern_debug = re.compile('\w*_debug_\w*')
	if not os.path.exists(config_dir):
		print 'Error: fail to find config_dir = ' + config_dir
		sys.exit(2)
	#
	flag_debug = 0
	for file_name in project_list.split('.'):
		if file_name == 'debug':
			# debug only
			flag_debug = 2
		elif file_name == 'user':
			# user only
			flag_debug = 1
		else:
			# project name
			project_name = file_name
	#
	if (project_name == 'ALL') or (project_name == 'COMMON') or (project_name == 'ELBRUS') or (pattern_platform.match(project_name)):
		for root, dirs, files in os.walk(config_dir):
			if config_dir == root:
				for file_name in files:
					flag = 0
					if project_name == 'ALL':
						flag = 1
					else:
						platform_name = get_mtk_platform(config_dir + os.sep + file_name)
						if (project_name == 'COMMON') and (platform_name != ''):
							flag = 1
						elif platform_name == project_name:
							flag = 1
					if flag == 1:
						if (flag_debug == 2) and (not pattern_debug.match(file_name)):
							flag = 0
						elif (flag_debug == 1) and (pattern_debug.match(file_name)):
							flag = 0
					if flag == 1:
						defconfig_list.append(config_dir + os.sep + file_name)
	elif os.path.exists(config_dir + os.sep + project_name + '_defconfig'):
		if (flag_debug == 0) or (flag_debug == 1):
			defconfig_list.append(config_dir + os.sep + project_name + '_defconfig')
		if os.path.exists(config_dir + os.sep + project_name + '_debug_defconfig'):
			if (flag_debug == 0) or (flag_debug == 2):
				defconfig_list.append(config_dir + os.sep + project_name + '_debug_defconfig')
	else:
		print 'Error: fail to find project_name = ' + project_name
		sys.exit(2)
	#
	option_list = []
	kernel_root = ''
	kernel_arch = ''
	if not option_file == 'KCONFIG':
		if not os.path.exists(option_file):
			print 'Error: fail to find option_file = ' + option_file
			sys.exit(2)
		file_handle = open(option_file, 'r')
		for line in file_handle.readlines():
			line_strip = line.strip()
			if not line_strip == '':
				option_list.append(line_strip)
		file_handle.close()
	for file_name in defconfig_list:
		print ''
		print ''
		print 'Read: ' + file_name
		res = 0
		if option_file == 'KCONFIG' or pattern_config.match(action_name):
			(kernel_root, kernel_arch) = get_kernel_root(file_name)
			if kernel_root == '':
				print 'Error: fail to get kernel path from ' + file_name
				res = 1
			if kernel_arch == '':
				print 'Error: fail to get kernel ARCH from ' + file_name
				res = 1
		if res == 0:
			if option_file == 'KCONFIG':
				res = exec_oldconfig(kernel_root, kernel_arch, file_name, action_name)
			elif action_name == 'update' or action_name == 'show':
				modify_defconfig(file_name, option_list, action_name)
				res = 0
			else:
				res = modify_exec_oldconfig(kernel_root, kernel_arch, file_name, option_list, action_name)
		if not res == 0:
			result = result + 1
	#
	print ''
	print ''
	if result == 0:
		print 'Done: all pass'
		sys.exit(0)
	else:
		print 'Done: some error happened'
		sys.exit(1)


if __name__ == '__main__':
	main(sys.argv)
