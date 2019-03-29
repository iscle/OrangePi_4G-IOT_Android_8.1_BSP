#!/usr/bin/python

import sys
import re
import getopt
import os

################################################################
def check_file(fname):
    """Check if a file contains option"""
    global exclude_list, blist, bkeys, listfile, option_keys, opt_times

    # Stripping CPP comment for *.[ch]
    cpp_comment = 0
    if re.search('\\.[ch]$', fname):
        cpp_comment = 1

    # Check what bin match for this file name.
    bmatch=dict()
    bfound = 0
    for j in bkeys:
        bmatch[j] = 0
        for k in blist[j]:
            if re.match(k, fname) or re.match('./' + k, fname):
                bmatch[j] = 1
                bfound = bfound + 1
                break
    if bfound == 0:
        bmatch['common'] = 1

    # Check exlcude list
    check_keys = option_keys
    for j in exclude_list.keys():
        if re.match(j, fname) or re.match('./' + j, fname):
            for k in exclude_list[j]:
                if k in check_keys:
                    check_keys.remove(k)

    f=open(fname, 'rt');
    lnum = 0
    check_string = 1
    multiline_comment = 0
    for line in f:
        lnum = lnum + 1
        if cpp_comment:
            if check_string and not multiline_comment:
                tline = re.sub(r'\'"\'', '', line);
                tline = re.sub(r'"([^\\"]|(\\.))*"', '', tline)
                if tline.find('"') >= 0:
                    #print 'x', line,
                    check_string = 0
                else:
                    #print ' ', line,
                    line = tline
            if not multiline_comment:
                line = re.sub(r'/\*.*?\*/', '', line)
            if multiline_comment:
                #print ' ', line,
                if line.find('*/') >= 0:
                    line = re.sub(r'.*?\*/', '', line)
                    multiline_comment=0
                    #print 'end'
                else:
                    continue
            line = re.sub(r'//.*' , '', line)
            if line.find('/*') >= 0:
                #print 'm', line,
                line = re.sub(r'/\*.*', '', line)
                multiline_comment=1

        found = 0
        for i in check_keys:
            if line.find(i) >= 0:
                if re.search('\\b' + i + '\\b', line):
                    found = found + 1
                    for j in bkeys:
                        opt_times[i][j] = opt_times[i][j] + bmatch[j]

        if found > 0:
            for j in bkeys:
                if bmatch[j]:
                    listfile[j].write(fname+ ' {0}:'.format(lnum)+ line)

    if multiline_comment:
         print "Warning multiline comment not finished ", fname
    if not check_string:
         print "Warning not checking string ", fname

    f.close

################################################################
def parse_config(fname):
    """Parsing config file and bring-in options"""
    global options, verbose, check_define_by_value
    options=dict()
    f=open(fname, 'rt');
    for line in f:
        # Ignore comment & empty line
        line = re.sub('#.*', '', line)
        line = line.strip()
        if len(line) == 0:
            continue

        # Adding keys/list
        list = re.split('[ =\t]+', line)
        key = list[0]
        del(list[0])
        options[key]=list
    f.close

    # Check GLOBAL_DEFINEs
    checks=['AUTO_ADD_GLOBAL_DEFINE_BY_NAME', 'AUTO_ADD_GLOBAL_DEFINE_BY_NAME_VALUE']
    for i in checks:
        vlist = options[i]
        for j in vlist:
            if j not in options:
                if verbose > 0:
                    print 'Adding key ' + j
                options[j] = []

    # Check DEFINE BY VALUE
    checks = []
    if check_define_by_value:
        checks = options['AUTO_ADD_GLOBAL_DEFINE_BY_VALUE']
    for i in checks:
        vlist = options[i]
        for j in vlist:
            value = j.upper()
            if value != 'YES' and value != 'NO' and value != '' and value not in options:
                options[value] = []
                if verbose > 0:
                    print 'Adding value ' + value + ' key ' + i


################################################################
def parse_excludes(fname):
    """Parsing exlcude listfile"""
    global exclude_list, verbose
    f=open(fname, 'rt');
    for line in f:
        # Ignore comment & empty line
        line = re.sub('#.*', '', line)
        line = line.strip()
        if len(line) == 0:
            continue

        list = re.split('[ =\t]+', line)
        key = list[0]
        del(list[0])
        if key in exclude_list:
            list = exclude_list[key] + list
        exclude_list[key]=list
    f.close



################################################################
def run_gen_defconfig():
    """Generate defconfig options base on ProjectConfig.mk"""
    global options, option_keys, verbose
    vlist = options['AUTO_ADD_GLOBAL_DEFINE_BY_NAME']
    for i in vlist:
        if len(options[i]) == 0 or options[i] == ['no']:
            print '# CONFIG_' + i + ' is not set'
        else:
            print 'CONFIG_' + i + '=y'

    vlist = options['AUTO_ADD_GLOBAL_DEFINE_BY_NAME_VALUE'] + options['AUTO_ADD_GLOBAL_DEFINE_BY_VALUE']
    for i in vlist:
        if i in options and len(options[i]) > 0:
            value = ''
            for j in options[i]:
                value = value + j + ' '
            print 'CONFIG_' + i + '=y'
            print 'CONFIG_' + i + '=\"' + value.rstrip(' ') + '\"'

    check_list = options['AUTO_ADD_GLOBAL_DEFINE_BY_NAME'] + vlist
    for i in option_keys:
        if i not in check_list and len(options[i]) > 0:
            if options[i] == ['no']:
                print '# CONFIG_' + i + ' is not set'
            elif options[i] == ['yes']:
                print 'CONFIG_' + i + '=y'
            else:
                value = ''
                for j in options[i]:
                    value = value + j + ' '
                print 'CONFIG_' + i + '=\"' + value.rstrip(' ') + '\"'


################################################################
def usage():
    """ Print usage help """
    print 'Usage:\n    ' + sys.argv[0] + ' [-h] [-v] [-V] [-x exlcude_file] [-o out_dir] -c config_name'
    print ''
    print '    -h     This help'
    print '    -v     Print verbose message'
    print '    -V     Also check define by value usage'
    print '    -o out_dir        Output dir file'
    print '    -x excldue_file   Exclude file list'
    print '    -c config_name    ProjectConfig.mk name'


################################################################
# Main function:

if len(sys.argv)>1 and (sys.argv[1] == 'gen_defconfig.py' or
    sys.argv[1] == 'gen_defconfig'):
        del(sys.argv[0])

# Get opts.
try:
    opts, args = getopt.getopt(sys.argv[1:], "hc:x:o:vV", ["help", "config=", "excldue=", "output=", "verbose", "value"])
except getopt.GetoptError as err:
    # print help information and exit:
    print str(err) # will print something like "option -a not recognized"
    usage()
    sys.exit(2)
config = ''
verbose = 0
check_define_by_value=0
exclude_file = ''
outdir = ''
for o, a in opts:
    if o == "-v":
        verbose = 1
    elif o in ("-h", "--help"):
        usage()
        sys.exit()
    elif o in ("-x", "--exclude"):
        exclude_file = a
    elif o in ("-c", "--config"):
        config = a
    elif o in ("-o", "--output"):
        outdir = a
        if outdir[-1] != '/':
            outdir = outdir + '/';
    elif o in ("-V", "--value"):
        check_define_by_value=1
    else:
        assert False, "unhandled option"

if config == '':
    usage()
    sys.exit()

# Bin list
blist=dict()
blist['kernel'] = ('kernel', 'mediatek/kernel', 'mediatek/platform/.*/kernel')
blist['mt8135'] = ('mediatek/platform/mt8135', 'mediatek/custom/mt8135')
blist['common'] = ()

excludes_dir = ('kernel/out', 'kernel/mediatek', 'out', 'mediatek/custom/out', 
                'vendor/mediatek/.*/artifacts/out', 'kernel/arch/arm/configs',
                'kernel/arch/arm/boot/dts', 'kernel/arch/m32r', 'kernel/arch/hexagon',
                'kernel/arch/x86', 'kernel/arch/mips', '.*/Kconfig[^/]*')


# Feature option list
parse_config(config)

exclude_list=dict()
option_keys=options.keys()
bkeys=blist.keys()
listfile=dict()

if os.path.basename(sys.argv[0]) == 'gen_defconfig.py':
    run_gen_defconfig()
    sys.exit()

if exclude_file and os.access(exclude_file, os.R_OK):
    parse_excludes(exclude_file)

for j in option_keys:
    print j

for j in bkeys:
    listfile[j]=open(outdir + 'list_' + j + '.txt', 'wt')

opt_times=dict()
for i in option_keys:
    opt_times[i] = dict()
    for j in bkeys:
        opt_times[i][j] = 0

# Search files, from stdin
fnames=sys.stdin
for fname in fnames:
    #f=open(fname, 'rt');
    #print f.readline
    #f.close
    fname = fname.rstrip('\n')

    # Check if the file is excluded.
    exclude = 0
    for i in excludes_dir:
        if re.match(i, fname) or re.match('./' + i, fname):
            exclude = 1
            break

    if exclude:
        if verbose > 0:
            print 'Ignore ' + fname
        continue
    
    check_file(fname)

# Close symbol files.
for j in bkeys:
    listfile[j].close()

# Open symole file.
symfile=dict()
for j in bkeys:
    symfile[j]=open(outdir + 'sym_' + j + '.txt', 'wt')

# Dump symbol usage status
for i in option_keys:
    for j in bkeys:
        if opt_times[i][j] > 0:
            symfile[j].write(i + ' {0}\n'.format(opt_times[i][j]))

for j in bkeys:
    symfile[j].close()
