#!/usr/bin/python -B
import sys, os, re, collections, json, argparse, logging
from fs_utils import feature_switch, extend_path, EXCLUDED_ARRT_KEY, update_build_vars
default_config_fn = 'vendor/mediatek/switch_config/${TARGET_BOARD_PLATFORM}/feature_switchable.all.json'

logger = logging.getLogger(__name__)
handler = logging.StreamHandler()
handler.setFormatter(logging.Formatter("%(module)s: %(levelname)s: %(message)s"))
logger.addHandler(handler)
logger.setLevel(logging.WARNING)

logger_plain = logging.getLogger(__name__+'_plain')
handler = logging.StreamHandler()
handler.setFormatter(logging.Formatter("%(message)s"))
logger_plain.addHandler(handler)
logger_plain.setLevel(logging.INFO)


def print_missed_repos_msg(fs, feature, repos):
    logger.error('License feature "{}" is ON: repo dependency check fail'.format(feature))
    logger.error('Missing repo: {}'.format(', '.join(repos)))
    try:
        msg = fs.show_switch_sop(feature, 'OFF')
        logger_plain.info('Follow below procedure to switch "{}" OFF:'.format(feature))
        for line in msg:
            logger_plain.info(line)
    except KeyError:
        pass


def check_repo_exist(fs):
    lic_features = fs.categories.get('license', [])
    excluded = set(fs.categories.get(EXCLUDED_ARRT_KEY, []))
    lic_features = [f for f in lic_features if f not in excluded]
    on_features = list()
    for feature in lic_features:
        d = fs.feature_status(feature)
        if d.get('ON', False):
            on_features.append(feature)
    has_missed_repo = False
    for feature in on_features:
        try:
            repos = fs.features[feature]['ON'][fs.target_configs['source']]['required']
            missed_repos = filter(lambda x: not os.path.exists(x), repos)
            # only alarm if none of repos exist
            if repos and len(missed_repos) == len(repos):
                print_missed_repos_msg(fs, feature, missed_repos)
                has_missed_repo = True
        except KeyError:
            logger.warning('no repo config for license feature: {}'.format(feature))
            continue
        logger.info('{}:'.format(feature))
        logger.info('\n'.join(repos))
    return not has_missed_repo


def setup_vars(var_key, vars_str):
    vd = {var_key: dict()}
    for var_val in vars_str.split(' '):
        var, val = var_val.split('=')
        vd[var_key][var] = val
    update_build_vars(vd)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('target_product', metavar='[TARGET_PRODUCT]',
                        help='TARGET_PRODUCT to check repo dep')
    parser.add_argument('--vars',
                        help='vars from build system')
    args = parser.parse_args()

    target_product = args.target_product
    vars_str = args.vars

    if vars_str:
        setup_vars(target_product, vars_str)

    try:
        config_fn = extend_path(default_config_fn, target_product)
    except Exception:
        logger.warning('error to get switch config file: {}'.format(default_config_fn))
        sys.exit(0)

    if not os.path.exists(config_fn):
        logger.warning('repo dep check bypass, config not exist: {}'.format(config_fn))
        sys.exit(0)

    fs = feature_switch(target_product, codepath='.', backup_config_files=False)
    fs.init_with_switch_config(config_fn)
    if not check_repo_exist(fs):
        sys.exit(9)

if __name__ == '__main__':
    main()