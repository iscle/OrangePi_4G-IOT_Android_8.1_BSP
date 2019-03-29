#!/usr/bin/env python
import os
import sys
import stat
import shutil
import re
import time


def getCommand2List(command):
	#print command
	result = os.popen(command).readlines()
	return result

def readGitStatus2Dict(line):
	key = None
	value = None
	items = re.split('\s+', line.strip())
	key = items[1]
	value = items[0]
	return (key, value)

def readRepoForAll2Dict(log_list):
	repo_forall = {}
	pattern_rule = re.compile('(REPO_\w+)=(\S+)')
	repo_path = ''
	for line in log_list:
		result = pattern_rule.match(line)
		if result:
			key = result.group(1)
			value = result.group(2)
			if key == 'REPO_PATH':
				repo_path = value
				repo_forall[repo_path] = {}
			else:
				repo_forall[repo_path][key] = value
		else:
			if not 'status' in repo_forall[repo_path]:
				repo_forall[repo_path]['status'] = {}
			(key, value) = readGitStatus2Dict(line)
			if not key in repo_forall[repo_path]['status']:
				repo_forall[repo_path]['status'][key] = ''
			repo_forall[repo_path]['status'][key] = repo_forall[repo_path]['status'][key] + value
	return repo_forall

def checkGitStatus(status):
	# +------------+---------------+
	# |            | unstaged      |
	# |            +---+---+---+---+
	# |            | ? | D | M | _ |
	# +--------+---+---+---+---+---+
	# | staged | A |   | _ | A | A |
	# |        +---+---+---+---+---+
	# |        | D | M |   |   | D |
	# |        +---+---+---+---+---+
	# |        | M |   | D | M | M |
	# |        +---+---+---+---+---+
	# |        | _ | A | D | M | _ |
	# +--------+---+---+---+---+---+
	if (status == 'A') or (status == 'D') or (status == 'M'):
		result = status
	elif status == ' ':
		result = ' '
	elif (status == '??') or (status == '?'):
		result = 'A'
	elif status == 'AD':
		result = ' '
	elif status == 'AM':
		result = 'A'
	elif (status == 'D??') or (status == 'D?'):
		result = 'M'
	elif status == 'MD':
		result = 'D'
	elif status == 'MM':
		result = 'M'
	else:
		#print 'Unknown git status: ' + status
		result = status
	return result

def diffGitStatus(old_status, new_status):
	# +---------+---------------+
	# |         | new           |
	# |         +---+---+---+---+
	# |         | A | D | M | _ |
	# +-----+---+---+---+---+---+
	# | old | A |   | x |   | x |
	# |     +---+---+---+---+---+
	# |     | D |   |   |   |   |
	# |     +---+---+---+---+---+
	# |     | M |   | x |   |   |
	# |     +---+---+---+---+---+
	# |     | _ |   | x |   |   |
	# +-----+---+---+---+---+---+
	old = checkGitStatus(old_status)
	new = checkGitStatus(new_status)
	if old == 'A' and new == 'D':
		result = 'clean'
	elif old == 'A' and new == ' ':
		result = 'clean'
	elif old == 'M' and new == 'D':
		result = 'clean'
	elif old == ' ' and new == 'D':
		result = 'clean'
	else:
		result = 'remake'
	return result

def diffRepoForAll2Dict(old_dict, new_dict):
	repo_diff = {}
	for repo_path in old_dict.keys():
		if repo_path in new_dict:
			# Check REPO_LREV
			git_diff = {}
			if not old_dict[repo_path]['REPO_LREV'] == new_dict[repo_path]['REPO_LREV']:
				#print repo_path + ' is different in REPO_LREV'
				git_diff_command = 'cd ' + repo_path + '; git diff --name-status --no-renames ' + old_dict[repo_path]['REPO_LREV'] + ' ' + new_dict[repo_path]['REPO_LREV']
				git_diff_lines = getCommand2List(git_diff_command)
				for line in git_diff_lines:
					(key, value) = readGitStatus2Dict(line)
					if not key in git_diff:
						git_diff[key] = ''
					git_diff[key] = git_diff[key] + value
			for f in git_diff.keys():
				old_status_file = ' '
				new_status_file = git_diff[f]
				action = diffGitStatus(old_status_file, new_status_file)
				if action == 'clean':
					repo_diff[os.path.join(repo_path, f)] = 'D'
				elif repo_path == 'build':
					repo_diff[os.path.join(repo_path, f)] = 'D'

			# Check git status
			file_list = set()
			if 'status' in old_dict[repo_path]:
				old_status_dict = old_dict[repo_path]['status']
				file_list.update(old_status_dict.keys())
			else:
				old_status_dict = {}
			if 'status' in new_dict[repo_path]:
				new_status_dict = new_dict[repo_path]['status']
				file_list.update(new_status_dict.keys())
			else:
				new_status_dict = {}
			for f in file_list:
				if f in old_status_dict:
					old_status_file = old_status_dict[f]
				else:
					old_status_file = ' '
				if f in new_status_dict:
					new_status_file = new_status_dict[f]
				else:
					new_status_file = ' '
				action = diffGitStatus(old_status_file, new_status_file)
				if action == 'clean':
					repo_diff[os.path.join(repo_path, f)] = 'D'
		else:
			repo_diff[repo_path + '/'] = 'D'
			#print repo_path + ' is removed from REPO_PATH'
#	for repo_path in new_dict.keys():
#		if not repo_path in old_dict:
#			print repo_path + ' is added to REPO_PATH'
	return repo_diff

def get_rm_intermediates(file_name, mtk_target_project):
	result = ''
	if file_name.find('vendor/mediatek/proprietary/modem/') == 0:
		pass
	elif file_name.endswith('.bak'):
		pass
	elif file_name.endswith('.log'):
		pass
	elif file_name.endswith('.exe'):
		pass
	elif file_name.endswith('.dll'):
		pass
	elif file_name.endswith('.bat'):
		pass
	elif file_name.endswith('/README'):
		pass
	elif file_name.endswith('/NOTICE'):
		pass
	elif file_name.endswith('/AUTHORS'):
		pass
	elif file_name.endswith('/CHANGES'):
		pass
	elif file_name.endswith('/CHANGELOG'):
		pass
	elif file_name.endswith('/COPYRIGHT'):
		pass
	elif file_name.endswith('/HISTORY'):
		pass
	elif file_name.endswith('/INSTALL'):
		pass
	elif file_name.endswith('/PROPRIETARY_LICENSE'):
		pass
	elif file_name.find('/MODULE_LICENSE_') != -1:
		pass
	elif file_name.find('vendor/mediatek/proprietary/tinysys/lk/') == 0:
		pass
	elif file_name.find('build/') == 0:
		result = 'out'
	elif file_name.find('device/mediatek/build/tasks/') == 0:
		result = 'out'
	elif file_name.find('device/mediatek/build/build/libs/') == 0:
		result = 'out'
	elif file_name.find('kernel') == 0:
		result = 'out/target/product/' + mtk_target_project + '/obj/KERNEL_OBJ'
	elif file_name.find('bootable/bootloader/preloader/') != -1:
		result = 'out/target/product/' + mtk_target_project + '/obj/PRELOADER_OBJ'
	elif file_name.find('bootable/bootloader/lk/') != -1:
		result = 'out/target/product/' + mtk_target_project + '/obj/BOOTLOADER_OBJ'
	elif file_name.find('vendor/mediatek/proprietary/trustzone/atf/') == 0:
		result = 'out/target/product/' + mtk_target_project + '/trustzone/ATF_OBJ'
	elif file_name.find('vendor/mediatek/proprietary/trustzone/') == 0:
		result = 'out/target/product/' + mtk_target_project + '/trustzone'
	elif file_name.find('trusty/') == 0:
		result = 'out/target/product/' + mtk_target_project + '/obj/TRUSTY_OBJ'
	elif file_name.find('vendor/mediatek/proprietary/tinysys/freertos/') == 0:
		result = 'out/target/product/' + mtk_target_project + '/obj/TINYSYS_OBJ'
	else:
		result = 'out'
	return result

def get_all_project_from_codebase(mtk_target_project, mtk_base_project, mtk_platform, target_board_platform):
	all_project = set()
	check_folders = ['device/mediatek',
			'vendor/mediatek/proprietary/custom',
			'vendor/mediatek/proprietary/bootable/bootloader/lk/platform',
			'vendor/mediatek/proprietary/bootable/bootloader/lk/target',
			'vendor/mediatek/proprietary/bootable/bootloader/preloader/platform',
			'vendor/mediatek/proprietary/bootable/bootloader/preloader/custom',
			'bootable/bootloader/lk/platform',
			'bootable/bootloader/lk/target',
			'bootable/bootloader/preloader/platform',
			'bootable/bootloader/preloader/custom',
			'vendor/mediatek/proprietary/trustzone/mtee/build/cfg',
			'trusty/vendor/mediatek/proprietary/platform']
	for check_path in check_folders:
		if os.path.exists(check_path):
			for listdir in os.listdir(check_path):
				if listdir == 'build' or listdir == 'common':
					pass
				else:
					all_project.add(listdir)
	mtk_platform_dir = mtk_platform.lower()
	if not mtk_platform_dir == 'mt6735':
		all_project.add('mt6735m')
		all_project.add('mt6753')
	all_project.discard(mtk_target_project)
	all_project.discard(mtk_base_project)
	all_project.discard(mtk_platform_dir)
	all_project.discard(target_board_platform)
	if mtk_platform_dir == 'mt6595' or target_board_platform == 'mt6595':
		all_project.discard('mt6795')
	return all_project

def check_build_action(repo_diff, all_project):
	repo_delete = {}
	for key in repo_diff.keys():
		flag_ignore = False
		for check_path in all_project:
			if key.find('/' + check_path + '/') != -1 or key.find('/' + check_path + '.') != -1 or key.find('.' + check_path + '.') != -1 or key.find('/' + check_path + '-') != -1 or key.find('/' + check_path + '_debug_defconfig') != -1 or key.find('/' + check_path + '_defconfig') != -1:
				flag_ignore = True
		if not flag_ignore:
			rm = get_rm_intermediates(key, '*')
			if not rm == '':
				#print key + ': ' + rm
				repo_delete[key] = rm
	return repo_delete

def main(argv):
	if len(argv) < 3:
		help(argv)
	#
	action = argv[1]
	repo_log = argv[2]
	repo_for_all_command = '.repo/repo/repo forall -c \'echo REPO_PATH=$REPO_PATH; echo REPO_PROJECT=$REPO_PROJECT; echo REPO_LREV=$REPO_LREV; git status -s\''

	if not os.path.exists('.repo'):
		# Skip if not .repo
		return 1

	if action == 'write':
		repo_forall_new_list = getCommand2List(repo_for_all_command)
		file_handle = open(repo_log, 'w')
		for line in repo_forall_new_list:
			file_handle.write(line)
		file_handle.close()
	elif action == 'diff':
		if len(argv) > 6:
			mtk_target_project = argv[3]
			mtk_base_project = argv[4]
			mtk_platform = argv[5]
			target_board_platform = argv[6]
			all_project = get_all_project_from_codebase(mtk_target_project, mtk_base_project, mtk_platform, target_board_platform)
		else:
			all_project = set()
		repo_forall_new_list = getCommand2List(repo_for_all_command)
		repo_forall_new_dict = readRepoForAll2Dict(repo_forall_new_list)
		file_handle = open(repo_log, 'r')
		repo_forall_old_list = file_handle.readlines()
		file_handle.close()
		repo_forall_old_dict = readRepoForAll2Dict(repo_forall_old_list)
		repo_diff = diffRepoForAll2Dict(repo_forall_old_dict, repo_forall_new_dict)
		repo_delete = check_build_action(repo_diff, all_project)
		if len(repo_delete.keys()):
			print ' '.join(repo_delete.keys())
	return 0


def help(argv):
	print argv[0] + ' write' + ' repo_forall.log'
	print argv[0] + ' diff' + ' repo_forall.log' + ' mtk_target_project mtk_base_project mtk_platform target_board_platform'
	print ''
	sys.exit(2)

if __name__ == '__main__':
	main(sys.argv)
