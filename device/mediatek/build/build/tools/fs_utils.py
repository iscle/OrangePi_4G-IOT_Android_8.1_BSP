import os, json, re, shutil, filecmp, sys, glob, collections, copy, ConfigParser
import xml.dom.minidom as xdom
import subprocess as sp
from xml.parsers.expat import ExpatError

################################################################################################################
# Global variables
DEBUG = 0
DEFAULT_MANIFEST_FILENAME = 'SwitchManifest.json'
DEFAULT_CONFIG_FILE_PREFIX = 'switchable_'
DEFAULT_CONFIG_FILE = os.path.join('vendor/mediatek/build/mptools/', DEFAULT_MANIFEST_FILENAME)
BUILD_VARS_CACHE_FILE = '.fs-vars-cache.json'
CONFIG_BACK_POSFIX = '.fs.backup'
CONFIG_BACK_NOT_EXIST_POSFIX = '.not-exist'

VAR_FUNCTIONS = {
    'dirname': os.path.dirname,
    'lower': unicode.lower
}
SWITCH_ACTIONS = ['set', 'remove_kconfig', 'insert_after', 'add', 'remove_block', 'remove']
BUILD_VARS = {}

EXCLUDED_ARRT_KEY = '_excluded_'

################################################################################################################
# Config classes
class TargetConfig(object):
    def __init__(self, name, path, vars_key, codepath):
        self.name = name
        self.path = path
        self.related_path = path
        if vars_key:
            # do not extend real_path in case of vars_key==None, this is useful to config converter case.
            self.real_path = self.related_path = extend_path(path, vars_key, codepath)
            if codepath:
                self.real_path = os.path.join(codepath, self.real_path)
            if 'device.mk' in self.real_path:
                self.__class__ = OrderedConfig
            elif self.real_path.endswith('.dts'):
                self.__class__ = OrderedConfig
            elif self.real_path.endswith('.rc') or self.real_path.endswith('_rc'):
                self.__class__ = OrderedConfig
            elif self.real_path.endswith('_defconfig'):
                self.__class__ = KernelConfig
            elif self.real_path.endswith('.mk') or self.real_path.endswith('.mak'):
                self.__class__ = MakeConfig
            elif 'CIPconfig.ini' in self.real_path:
                self.__class__ = MakeConfig
            else:
                self.__class__ = NOPConfig
                # raise Exception('Unknown config type: %s' % self.real_path)

    def get_config_backup_filename(self):
        path = self.real_path+CONFIG_BACK_POSFIX
        not_exist_backup_path = path + CONFIG_BACK_NOT_EXIST_POSFIX
        if os.path.exists(not_exist_backup_path) or not os.path.exists(self.real_path):
            return not_exist_backup_path
        return path

    def original_config_file_exist(self):
        backup = self.get_config_backup_filename()
        if os.path.exists(backup):
            if backup.endswith(CONFIG_BACK_NOT_EXIST_POSFIX):
                return False
            else:
                return True
        else:
            return os.path.exists(self.real_path)

    def config_file_backup(self):
        backup_file = self.get_config_backup_filename()
        if not os.path.exists(backup_file):
            if os.path.exists(self.real_path):
                shutil.copy(self.real_path, backup_file)
            else:
                if os.path.exists(os.path.dirname(backup_file)):
                    open(backup_file, 'a').close()
            return True
        return False

    def config_file_restore(self):
        backup_file = self.get_config_backup_filename()
        if os.path.exists(backup_file):
            if self.original_config_file_exist():
                if not filecmp.cmp(self.real_path, backup_file):
                    shutil.copy(backup_file, self.real_path)
                    # message('Restore %s' % self.real_path)
                    return self.real_path
            elif os.path.exists(self.real_path):
                os.remove(self.real_path)
                return self.real_path
        return None

    def modify(self, actions, modify_file=True):
        log_tags = ['WARNING', 'UPDATE', 'REMOVE_LINE', 'NEW_LINE', 'NO_CHANGE']
        log_list = ['\tFILE: %s' % self.related_path]
        log = collections.defaultdict(list)

        if not os.path.exists(self.real_path):
            raise Exception('Config file: %s not exits' % self.real_path)

        for aname in SWITCH_ACTIONS:
            if aname in actions:
                try:
                    method = getattr(self, 'action_%s' % aname)
                except:
                    raise Exception('Action method for "%s" not implemented' % aname)
                method(actions[aname], log, modify_file)
        for tag in log_tags:
            log_list.extend(['\t\t%s: line %s' %
                             (tag, msg) for msg in sorted(log[tag], key=lambda k: int(k.split()[0]))])
        return log_list

    def action_set(self, content, log, modify_file):
        lines = open(self.real_path).readlines()
        new_lines = []
        for fv in content:
            to_replace_matched, new_value_matched = self.get_set_pattern(fv)
            out_lines = []
            modified = False
            for ln in range(0, len(lines)):
                line = lines[ln].rstrip('\r\n')
                if new_value_matched.match(line):
                    log['NO_CHANGE'].append('%d "%s"' % (ln, line))
                    if DEBUG: log['NO_CHANGE'].append('%d >%s<' % (ln, fv))
                    out_lines.append(line)
                    modified = True
                elif to_replace_matched.match(line):
                    log['UPDATE'].append('%d "%s" => "%s"' % (ln+1, line, fv))
                    out_lines.append(fv)
                    modified = True
                else:
                    out_lines.append(line)
            lines = out_lines
            if not modified:
                new_lines.append(fv)

        last_ln = len(lines)
        for ln in range(0, len(new_lines)):
            line = new_lines[ln]
            log['NEW_LINE'].append('%d "%s"' % (last_ln+ln+1, line))
            lines.append(line)
        self.write_config_file(lines, modify_file)

    def action_add(self, content, log, modify_file):
        fn = open(self.real_path)
        last_ln = sum(1 for _ in fn)
        fn.seek(0)
        org_lines = map(str.rstrip, fn.readlines())
        adding_lines = []
        for lines in content:
            for line in lines.split('\n'):
                last_ln += 1
                log['NEW_LINE'].append('%d "%s"' % (last_ln, line))
                adding_lines.append(line)
        org_lines.extend(adding_lines)
        self.write_config_file(org_lines, modify_file)

    def action_remove(self, content, log, modify_file):
        lines = map(str.strip, open(self.real_path).readlines())
        for remove_line in content:
            for ln in range(0, len(lines)):
                    if remove_line == lines[ln]:
                        log['REMOVE_LINE'].append('%d "%s"' % (ln, remove_line))
                        lines[ln] = ''
        self.write_config_file(lines, modify_file)

    def write_config_file(self, lines, modify_file):
        if modify_file:
            open(self.real_path, 'w').write('\n'.join(lines)+'\n')

    def dump_actions(self, actions):
        logs = [self.real_path]
        for action, content in actions.iteritems():
            for line in content:
                logs.append('\t[%s] %s' % (action.upper(), line))
        return logs

    def get_set_pattern(self, fv):
        # return re_to_replace, re_already_match_to_replace
        # re_to_replace: line matches this pattern will update to new feature-option
        # re_already_match_to_replace: line matches this pattern means the line is already same as new feature-option
        raise Exception('Should not go into here! fv=%s'%fv)

    def export_frxml(self, actions):
        raise NotImplementedError('no export_frxml implemented for {}'.format(self))

    def export_frxml_set_start(self, actions):
        xdoc = xdom.Document()
        n_config = xdoc.createElement('config')
        n_config.setAttribute('name', re.sub(r'^\./', '', self.real_path))
        if len(actions.keys()) > 1 or 'set' not in actions:
            raise UserWarning('export fr-xml only support set action, action for {} is: {}'.format(self.path, actions))
        return n_config

    def match_yesno(self, actions):
        rec_yesno_fo = re.compile(r'.*?=\s?(yes|no)')
        set_list = actions.get('set', [])
        filter(rec_yesno_fo.match, set_list)
        lines = [l.strip() for l in open(self.real_path).readlines()]
        match_count = 0
        for fv in set_list:
            to_replace_matched, new_value_matched = self.get_set_pattern(fv)
            for line in lines:
                if new_value_matched.match(line):
                    match_count += 1
                    break
        return (match_count>0) and (match_count==len(set_list))

    def __repr__(self):
        return self.name

class NOPConfig(TargetConfig):
    def modify(self, actions, modify_file=True):
        return []

    def config_file_backup(self):
        pass

    def config_file_restore(self):
        pass

    def export_frxml(self, actions):
        xdoc = xdom.Document()
        if len(actions.keys()) > 1 or 'unrequired' not in actions:
            raise UserWarning('export fr-xml only support unrequired action, action for {} is: {}'.format(self.path, actions))
        content_list = actions.get('unrequired', []) # now export frxml only support unrequired action
        element_list = list()
        for content in content_list:
            n_repo_file = xdoc.createElement('repo_file')
            n_repo_file.setAttribute('name', content)
            n_repo_file.setAttribute('action', 'delete')
            element_list.append(n_repo_file)
        return element_list

class MakeConfig(TargetConfig):
    def __init__(self, name, path, vars_key, codepath):
        super(MakeConfig, self).__init__(name, path, vars_key, codepath)

    def get_set_pattern(self, fv):
        try:
            fname, value = re.match(r'(\S+?)\s*:?=\s*(.+)?', fv).groups()
        except AttributeError:
            raise Exception('Error parase feature option: %s' % fv)
        return re.compile(r'%s\s*?:?=' % fname), re.compile(r'%s\s*?:?=\s*?%s$' % (fname, str(value or '')))

    def export_frxml(self, actions):
        xdoc = xdom.Document()
        n_config = self.export_frxml_set_start(actions)
        content_list = actions.get('set', [])  # now export frxml only support set action
        for content in content_list:
            try:
                fname, value = re.match(r'(\S+?)\s*:?=\s*(.+)?', content).groups()
            except AttributeError:
                raise UserWarning('Error parase feature option: %s' % content)
            n_set = xdoc.createElement('set')
            n_set.setAttribute('name', fname)
            n_set.setAttribute('content', value)
            n_config.appendChild(n_set)
        return n_config

class KernelConfig(TargetConfig):
    def __init__(self, name, path, vars_key, codepath):
        super(KernelConfig, self).__init__(name, path, vars_key, codepath)

    def get_set_pattern(self, fv):
        try:
            conf = max(re.match(r'(?:#\s*?(\S+) is not set)|(?:(\S+)=\S+)', fv).groups())
        except AttributeError:
            raise Exception('Error parase feature option: %s' % fv)
        return re.compile(r'(# %s is not set)|(%s=\S+)' % (conf, conf)), re.compile(r'%s' % fv)

    def action_remove_kconfig(self, content, log, modify_file):
        lines = map(str.strip, open(self.real_path).readlines())
        to_remove_lines = []
        for fv in content:
            to_replace_matched = re.compile(r'(# %s is not set)|(%s=\S+)' % (fv, fv))
            for ln in range(0, len(lines)):
                line = lines[ln].rstrip('\r\n')
                if to_replace_matched.match(line):
                    log['REMOVE_LINE'].append('%d "%s"' % (ln, line))
                    if DEBUG: log['REMOVE_LINE'].append('%d >%s<' % (ln, fv))
                    to_remove_lines.append(line)

        lines = [ line for line in lines if line.rstrip('\r\n') not in to_remove_lines]
        self.write_config_file(lines, modify_file)

    def export_frxml(self, actions):
        xdoc = xdom.Document()
        n_config = self.export_frxml_set_start(actions)
        rec_not_set = re.compile(r'#\s*?(\S+) is not set')
        rec_val = re.compile(r'(\S+)\s*?=\s*?(\S+)')
        content_list = actions.get('set', [])
        for content in content_list:  # now export frxml only support set action
            fo = val = None
            m = rec_not_set.match(content)
            if m:
                fo = m.group(1)
                val = 'n'
            m = rec_val.match(content)
            if m:
                fo = m.group(1)
                val = m.group(2)
                val = re.sub(r'(^"|"$)', '#', val)
            if fo is None:
                raise UserWarning('Error parase feature option: %s' % content)
            n_set = xdoc.createElement('set')
            n_set.setAttribute('name', fo)
            n_set.setAttribute('content', val)
            n_config.appendChild(n_set)
        return n_config

class OrderedConfig(TargetConfig):
    def __init__(self, name, path, vars_key, codepath):
        super(OrderedConfig, self).__init__(name, path, vars_key, codepath)

    def modify(self, actions, modify_file=True):
        if not os.path.exists(self.real_path):
            # Create init.rc file if not exist
            open(self.real_path, 'w').close()
        return super(OrderedConfig, self).modify(actions, modify_file)

    def action_add(self, content, log, modify_file):
        to_remove_lines = []
        for lines in content:
            lines_lst = str(lines).split('\n')
            ln_exist = self.find_ln_after_match(self.real_path, lines_lst)
            if ln_exist:
                log['NO_CHANGE'].extend(['%d "%s"'%(ln_exist+n+1, line) for n, line in enumerate(lines_lst)])
                to_remove_lines.append(lines)
                continue
        content_remain = [lines for lines in content if lines not in to_remove_lines]
        super(OrderedConfig, self).action_add(content_remain, log, modify_file)

    def action_insert_after(self, content, log, modify_file):
        for text in content:
            text = str(text).strip()
            text_lst = text.split('\n')
            try:
                idx = text_lst.index('>>insert<<')
            except ValueError:
                raise Exception('Error insert action, can not find >>insert<< tag:\n%s' % content)
            to_match = text_lst[0:idx]
            to_insert = text_lst[idx+1:]
            # check if insert block already exist
            ln_exist = self.find_ln_after_match(self.real_path, to_insert)
            if ln_exist:
                log['NO_CHANGE'].extend(['%d "%s"'%(ln_exist+n+1, line) for n, line in enumerate(to_insert)])
                continue
            if idx==0: #insert at top
                ln_to_insert = 0
            else:
                ln_to_insert = self.find_ln_after_match(self.real_path, to_match)
            lines = open(self.real_path).readlines()
            lines = map(str.rstrip, lines)
            if ln_to_insert is None:
                log['WARNING'].append('0 can not find "%s" for action insert_after, insert from tail' % to_match)
                ln_to_insert = len(lines)
            # insert into lines
            lines[ln_to_insert:0] = to_insert
            log['NEW_LINE'].extend(['%d "%s"'%(ln_to_insert+n+1, line) for n, line in enumerate(to_insert)])
            self.write_config_file(lines, modify_file)

    def action_remove_block(self, content, log, modify_file):
        for text in content:
            text = str(text)
            text_lst = text.split('\n')
            ln_end_remove = self.find_ln_after_match(self.real_path, text_lst)
            if ln_end_remove:
                lines = open(self.real_path).readlines()
                lines = map(str.rstrip, lines)
                del lines[ln_end_remove-len(text_lst):ln_end_remove]
                log['REMOVE_LINE'].extend(['%d "%s"'%(ln_end_remove-len(text_lst)+n+1, line) for n, line in enumerate(text_lst)])
                self.write_config_file(lines, modify_file)
            else:
                #raise Exception('Can not find "%s" for action remove_block, file: %s'%(text, self.real_path))
                log['NO_CHANGE'].extend(['0 "%s"'%line for line in text_lst])

    @staticmethod
    def find_ln_after_match(filename, to_match):
        lines = open(filename).readlines()
        to_match_ln = len(to_match)
        to_match_normalize = '\n'.join(map(str.strip, to_match))
        for ln in range(0, len(lines)):
            text = lines[ln:ln+to_match_ln]
            text_normalize = '\n'.join(map(str.strip, text))
            if to_match_normalize == text_normalize:
                return ln+to_match_ln
        return None

class feature_switch(object):
    def __init__(self, target_product, codepath=None, backup_config_files=True):
        self.target_product = target_product
        self.codepath = codepath
        self.backup_config_files = backup_config_files
        self.target_configs = {}
        self.features = collections.defaultdict(dict)
        self.categories = collections.defaultdict(list)
        self.fattr = collections.defaultdict(dict)

    def dump_json(self, out_json):
        def json_serialize(obj):
            if isinstance(obj, TargetConfig):
                return obj.path
            return obj.__dict__

        feature_dict = collections.defaultdict(dict)
        for fname, option_dict in self.features.items():
            for opname, config_dict in option_dict.items():
                str_config_dict = dict()
                for config_obj, action_dict in config_dict.items():
                    str_config_dict[str(config_obj)] = action_dict
                feature_dict[fname][opname] = str_config_dict

        results = {
            '__format_version__': '1.0.0',
            'config_files': self.target_configs,
            'features': feature_dict,
            'categories': self.categories,
            'fattr': self.fattr
        }
        if type(out_json) == str:
            out_fp = open(out_json, 'w')
        else:
            out_fp = out_json
        json.dump(results, out_fp, indent=4, sort_keys=True, default=json_serialize)

    def dump_frxml(self, feature_option_list, out_xml_fn):
        xdoc = xdom.Document()
        n_fr = xdoc.createElement('feature_release')
        for feature, option in sorted(feature_option_list):
            n_internal = xdoc.createElement('thirdparty')
            n_internal.setAttribute('name', feature)
            for config in sorted(self.features[feature][option].keys()):
                actions = self.features[feature][option][config]
                element = config.export_frxml(actions)
                if type(element) != list:
                    element = [element]
                for e in element:
                    n_internal.appendChild(e)
            n_fr.appendChild(n_internal)
        xdoc.appendChild(n_fr)
        with open(out_xml_fn, 'w') as fp:
            xdoc.writexml(fp, indent='    ', addindent='    ', newl='\n')

    # call this function to initialize feature_switch
    def init_with_default_config(self):
        filepath = extend_path(DEFAULT_CONFIG_FILE, self.target_product, self.codepath)
        if self.codepath:
            filepath = os.path.join(self.codepath, filepath)
        return self.init_with_switch_config(filepath)

    def init_with_switch_config(self, switch_config):
        return self.set_switch_config(switch_config)

    def set_switch_config(self, switch_config_file):
        if os.path.exists(switch_config_file):
            if os.path.basename(switch_config_file) == DEFAULT_MANIFEST_FILENAME:
                switch_config_files = self.load_from_manifest(switch_config_file)
            else:
                self.load_switchable_configs(switch_config_file)
                switch_config_files = [switch_config_file]
            #self.get_feature_list()
            if self.target_product:
                self.backup_target_config()
            return switch_config_files
        else:
            raise Exception('Switch config file not exist: %s'%switch_config_file)

    def switch_feature(self, feature, option, restore_first=True, modify_file=True, backup_config_root=None):
        if feature not in self.features:
            raise Exception('Unrecognized feature: %s' % feature)
        if option not in self.features[feature]:
            raise Exception('Unrecognized feature: %s, option: %s' % (feature, option))

        logs = []
        if restore_first:
            logs.append('Restore original config files.')
            self.restore_target_config()

        new_root=org_root=new_file=action_file=None
        if backup_config_root:
            org_root = os.path.join(backup_config_root, 'original')
            new_root = os.path.join(backup_config_root, 'modified')
            action_file = os.path.join(new_root, '%s--%s' % (feature, option), 'fs-config.txt')
            if os.path.exists(action_file):
                os.remove(action_file)

        for config in sorted(self.features[feature][option].keys()):
            actions = self.features[feature][option][config]
            if backup_config_root:
                if self.codepath:
                    real_path = config.real_path.replace(self.codepath, '').lstrip('/')
                else:
                    real_path = config.real_path
                org_file = os.path.join(org_root, '%s--%s' % (feature, option), real_path)
                new_file = os.path.join(new_root, '%s--%s' % (feature, option), real_path)

                if restore_first:
                    config.config_file_restore()
                if os.path.exists(config.real_path):
                    copyto(config.real_path, org_file)
            # do-modify config file
            logs.extend(config.modify(actions, modify_file))
            if backup_config_root:
                copyto(config.real_path, new_file)
                with open(action_file, 'a+') as fn:
                    fn.write('\n'.join(config.dump_actions(actions))+'\n')
        return logs

    def feature_status(self, feature):
        result_dict = dict()
        # result_dict={option1: true, option2: false, ...}
        for option, config_dict in self.features[feature].items():
            try:
                project_config = self.target_configs['projectconfig']
            except IndexError:
                result_dict[option] = False
                continue
            result_dict[option] = project_config.match_yesno(config_dict[project_config])
        return result_dict

    def show_switch_sop(self, feature, option):
        msg = list()
        for config in sorted(self.features[feature][option].keys()):
            actions = self.features[feature][option][config]
            if config.path == '':
                continue
            msg.append('  modify {}'.format(config.real_path))
            for action, content_list in actions.items():
                msg.append('    {}'.format(action))
                for content in content_list:
                    msg.append('      {}'.format(content))
        return msg

    def feature_filter(self, feature_list, key_str, config_file):
        config = ConfigParser.RawConfigParser()
        config.read(config_file)
        excluded_features = list()
        for feature_x in config.sections():
            for feature in list_match_wildcard(feature_x, feature_list):
                try:
                    for option in config.options(feature_x):
                        matched = False
                        for condition in config.get(feature_x, option).split(';'):
                            if match_wildcard(condition, key_str):
                                matched = True
                        if (option == 'not_support' and matched) or (option == 'only_support' and not matched):
                            excluded_features.append(feature)
                except (KeyError, ValueError):
                    pass
        excluded_features = list(set(excluded_features))
        self.assign_category(excluded_features, EXCLUDED_ARRT_KEY)

    def merge_source(self, repo_xml_fn):
        # ugly
        self.target_configs['source'] = TargetConfig('source', '', self.target_product, self.codepath)
        self.target_configs['internal'] = TargetConfig('internal', '', self.target_product, self.codepath)
        self.target_configs['brm'] = TargetConfig('brm', '', self.target_product, self.codepath)

        feature_list = list()
        repo_dict = load_repo_xml(repo_xml_fn)
        for feature, repo_list in repo_dict.items():
            if feature not in self.features:
                self.features[feature] = collections.defaultdict(dict)
            self.features[feature]['ON'][self.target_configs['source']] = {'required': repo_list}
            self.features[feature]['OFF'][self.target_configs['source']] = {'unrequired': repo_list}
            feature_list.append(feature)
        return feature_list

    def assign_category(self, feature_list, category):
        if not category:
            category = ['DEFAULT']
        elif type(category) != list:
            category = [category]
        for cat in category:
            self.categories[cat].extend(feature_list)
            self.categories[cat] = sorted(list(set(self.categories[cat])))

    def load_from_manifest(self, switch_manifest_file):
        try:
            switchable_config_files = list()
            rec = re.compile(r'%s(\S+)\.(xml|json)' % DEFAULT_CONFIG_FILE_PREFIX)
            mf = json.load(open(switch_manifest_file))
            switch_manifest_dir = os.path.dirname(switch_manifest_file)
            for fdict in mf.get('switchable_files').values():
                category = fdict.get('category_default', [])
                if type(category) != list:
                    category = [category]
                feature_list = list()
                for p in fdict.get('path', []):
                    try:
                        config_dir = os.path.join(switch_manifest_dir, extend_path(p, self.target_product, self.codepath))
                        for fn in glob.glob(config_dir.rstrip('/')+'/*'):
                            m = rec.match(os.path.basename(fn))
                            if m:
                                cat_from_filename = m.group(1)
                                cat_local = copy.copy(category)
                                cat_local.append(cat_from_filename)
                                cat_local = list(set(cat_local))
                                feature_list_from_filename = self.load_switchable_configs(fn, cat_local)
                                feature_list.extend(feature_list_from_filename)
                                switchable_config_files.append(fn)
                    except KeyError:
                        pass
                # merge source path after filter, so that all source can be merged even the feature is not exist
                for repo_xml_file in fdict.get('merge_source', []):
                    feature_list.extend(self.merge_source(os.path.join(switch_manifest_dir, repo_xml_file)))

                feature_list = list(set(feature_list))
                if category:
                    self.assign_category(feature_list, category)

                for filter_dic in fdict.get('filter', []):
                    key_str = extend_path(filter_dic['key'], self.target_product, self.codepath)
                    filter_file = os.path.join(switch_manifest_dir, filter_dic['config'])
                    self.feature_filter(feature_list, key_str, filter_file)

        except ValueError:
            raise UserWarning('failed to load manifest file: {}'.format(switch_manifest_file))
        return switchable_config_files

    def load_switchable_configs(self, switch_config_file, category=None):
        loaded_feature_list = list()
        if os.path.splitext(switch_config_file)[1] == '.xml':
            try:
                # read configs
                xroot = xdom.parse(switch_config_file).documentElement
                for xconfig_file in xroot.getElementsByTagName('config_file'):
                    config_name = xconfig_file.getAttribute('name')
                    path = None
                    for node in xconfig_file.getElementsByTagName('path'):
                        path = node.childNodes[0].nodeValue
                    for node in xconfig_file.getElementsByTagName('path_override'):
                        path = node.childNodes[0].nodeValue
                    if not path:
                        raise Exception('Failed to get path for config: %s' % config_name)
                    self.target_configs[config_name] = TargetConfig(config_name, path, self.target_product, self.codepath)
                # read features
                for feature in xroot.getElementsByTagName('feature'):
                    fname = feature.getAttribute('name')
                    for attr in feature.attributes.values():
                        self.fattr[fname][attr.name] = attr.value.replace('\\n', '\n')
                    try:
                        del self.fattr[fname]['name']
                    except KeyError:
                        pass

                    self.features[fname] = {}
                    for status in feature.getElementsByTagName('switch'):
                        sname = status.getAttribute('option')
                        self.features[fname][sname] = {}
                        for config in status.getElementsByTagName('config'):
                            for cname in config.getAttribute('name').split(' '):
                                if cname not in self.target_configs:
                                    raise Exception('Can not find config: %s for feature %s' % (cname, fname))
                                self.features[fname][sname][self.target_configs[cname]] = {}

                                for a in SWITCH_ACTIONS:
                                    for node in config.getElementsByTagName(a):
                                        self.features[fname][sname][self.target_configs[cname]].setdefault(a, []).append(
                                            node.childNodes[0].nodeValue)
                    loaded_feature_list.append(fname)
                self.assign_category(loaded_feature_list, category)
            except (IndexError, ExpatError):
                raise Exception('Failed to read config path from config file: %s' % switch_config_file)
        elif os.path.splitext(switch_config_file)[1] == '.json':
            data = json.load(open(switch_config_file))
            try:
                for config_name, path in data['config_files'].iteritems():
                    self.target_configs[config_name] = TargetConfig(config_name, path, self.target_product, self.codepath)
                for feature, option_dict in data['features'].iteritems():
                    for option, config_dict in option_dict.iteritems():
                        self.features[feature][option] = collections.defaultdict(dict)
                        for config, action_dict in config_dict.iteritems():
                            for cname in config.split(' '):
                                if cname not in self.target_configs:
                                    raise Exception('Can not find config: %s for feature %s' % (cname, feature))
                                for action, content_list in action_dict.iteritems():
                                    self.features[feature][option][self.target_configs[cname]].setdefault(action, []).extend(content_list)
                    loaded_feature_list.append(feature)
                if 'categories' in data:
                    self.categories = copy.deepcopy(data['categories'])
                if 'fattr' in data:
                    self.fattr = copy.deepcopy(data['fattr'])
            except KeyError:
                raise Exception('Failed to read config path from config file: %s' % switch_config_file)
        else:
            raise Exception('Unrecognized config type: %s' % switch_config_file)
        return loaded_feature_list

    def backup_target_config(self):
        if self.backup_config_files:
            for cf in self.target_configs.values():
                cf.config_file_backup()

    def restore_target_config(self):
        if self.backup_config_files:
            restored = []
            for cf in self.target_configs.values():
                rfile = cf.config_file_restore()
                if rfile:
                    restored.append(rfile)
            return restored

    def remove_target_config(self):
        if self.backup_config_files:
            removed = []
            for cf in self.target_configs.values():
                bkfile = cf.get_config_backup_filename()
                if os.path.exists(bkfile):
                    os.remove(bkfile)
                    removed.append(bkfile)
            return removed

################################################################################################################
# utilities
def load_repo_xml(repo_xml):
    repo_dict = collections.defaultdict(list)
    xroot = xdom.parse(repo_xml).documentElement
    for feature_n in xroot.getElementsByTagName('thirdparty'):
        fname = feature_n.getAttribute('name')
        for repo_n in feature_n.getElementsByTagName('repository'):
            repo = repo_n.getAttribute('name')
            repo_dict[fname].append(repo)
    return repo_dict

def list_match_wildcard(wildcard_str, test_list):
    return [x for x in test_list if match_wildcard(wildcard_str, x)]

def match_wildcard(wildcard_str, test_str):
    wildcard_str = wildcard_str.replace('*', r'.*')
    wildcard_str = wildcard_str.replace('?', r'\w')
    return re.match(wildcard_str, test_str)

def run(cmds):
    devnull = open(os.devnull, 'wb')
    child = sp.Popen(cmds, shell=True, stdout=sp.PIPE, stderr=devnull, close_fds=True)
    streamdata = child.communicate()[0]
    rc = child.returncode
    return rc, unicode(streamdata)


def extend_path(path, vars_key, codepath=None):
    if not path:
        return ''
    real_path = path
    m = re.match(r'##(.+)##', path)
    if m:
        cd_codepath = 'cd {} &&'.format(codepath) if codepath else ''
        cmd = '{} {}'.format(cd_codepath, m.group(1))
        ret, out = run(cmd)
        if ret != 0:
            raise Exception('Failed to run cmd from extend_path: {}'.format(cmd))
        real_path = out.strip()
    else:
        for keyword in re.findall(r'\$\{.*?\}', path):
            func_match = re.match(r'\$\{(\S+)?\s+(\S+)\}', keyword)
            if func_match:  # ${func variable}
                ftxt, var = func_match.groups()
                if ftxt not in VAR_FUNCTIONS:
                    raise Exception('Can not find vairable function: %s on extending variable %s' % (ftxt, keyword))
                value = VAR_FUNCTIONS[ftxt](get_build_var(var, vars_key, codepath))
            else:
                value = get_build_var(re.match(r'\$\{(\S+)\}', keyword).groups()[0], vars_key, codepath)
            real_path = real_path.replace(keyword, value)

    return real_path


def update_build_vars(var_dict):
    global BUILD_VARS
    BUILD_VARS.update(var_dict)


def get_build_var(var, vars_key, codepath=None, nocache=False):
    if vars_key not in BUILD_VARS:
        BUILD_VARS[vars_key] = {}
    if var not in BUILD_VARS[vars_key] or nocache:
        message('....Get build var %s = ' % var, True)
        if codepath:
            # codepath is not None, means get_build_var is not called in lunch-ed env.
            cd_path = 'cd %s;' % codepath
        else:
            cd_path = ''

        cmd = '%s make TARGET_PRODUCT=%s CALLED_FROM_SETUP=true BUILD_SYSTEM=build/core ' \
              '--no-print-directory -f build/core/config.mk dumpvar-%s' % (cd_path, vars_key, var)
        ret, out = run(cmd)
        if ret != 0:
            raise Exception("Failed to get_build_var with variable '%s'\ncmd: %s" % (var, cmd))
        val = out.strip('\n')
        if not val:
            raise Exception("Can not get_build_var: %s" % var)
        message(val)
        BUILD_VARS[vars_key][var] = val
    return BUILD_VARS[vars_key][var]


def reset_build_vars_cache(vars_key):
    if vars_key in BUILD_VARS:
        BUILD_VARS.pop(vars_key)


def load_build_vars_cache(cache_dir=None):
    global BUILD_VARS
    if cache_dir:
        if not os.path.exists(cache_dir):
            os.makedirs(cache_dir)
        cache_file = os.path.join(cache_dir, BUILD_VARS_CACHE_FILE)
    else:
        cache_file = BUILD_VARS_CACHE_FILE
    if os.path.exists(cache_file):
        BUILD_VARS = json.load(open(cache_file))


def save_build_vars_cache(cache_dir=None):
    if cache_dir:
        cache_file = os.path.join(cache_dir, BUILD_VARS_CACHE_FILE)
    else:
        cache_file = BUILD_VARS_CACHE_FILE
    json.dump(BUILD_VARS, open(cache_file, 'w'), indent=4)


def make_dir_of_file(filename):
    if not os.path.exists(filename):
        dest_dir = os.path.dirname(filename)
        if dest_dir != '' and not os.path.exists(dest_dir):
            os.makedirs(dest_dir)


def copyto(src, dest):
    make_dir_of_file(dest)
    shutil.copy(src, dest)


def message(msg, nonewline=False):
    if nonewline:
        sys.stdout.write(msg)
        sys.stdout.flush()
    else:
        print msg