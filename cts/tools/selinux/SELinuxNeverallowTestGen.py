#!/usr/bin/env python

import re
import sys
import SELinuxNeverallowTestFrame

usage = "Usage: ./SELinuxNeverallowTestGen.py <input policy file> <output cts java source>"


class NeverallowRule:
    statement = ''
    treble_only = False

    def __init__(self, statement):
        self.statement = statement
        self.treble_only = False


# extract_neverallow_rules - takes an intermediate policy file and pulls out the
# neverallow rules by taking all of the non-commented text between the 'neverallow'
# keyword and a terminating ';'
# returns: a list of rules
def extract_neverallow_rules(policy_file):
    with open(policy_file, 'r') as in_file:
        policy_str = in_file.read()

        # full-Treble only tests are inside sections delimited by BEGIN_TREBLE_ONLY
        # and END_TREBLE_ONLY comments.

        # uncomment TREBLE_ONLY section delimiter lines
        remaining = re.sub(
            r'^\s*#\s*(BEGIN_TREBLE_ONLY|END_TREBLE_ONLY)',
            r'\1',
            policy_str,
            flags = re.M)
        # remove comments
        remaining = re.sub(r'#.+?$', r'', remaining, flags = re.M)
        # match neverallow rules
        lines = re.findall(
            r'^\s*(neverallow\s.+?;|BEGIN_TREBLE_ONLY|END_TREBLE_ONLY)',
            remaining,
            flags = re.M |re.S)

        # extract neverallow rules from the remaining lines
        rules = list()
        treble_only_depth = 0
        for line in lines:
            if line.startswith("BEGIN_TREBLE_ONLY"):
                treble_only_depth += 1
                continue
            elif line.startswith("END_TREBLE_ONLY"):
                if treble_only_depth < 1:
                    exit("ERROR: END_TREBLE_ONLY outside of TREBLE_ONLY section")
                treble_only_depth -= 1
                continue
            rule = NeverallowRule(line)
            rule.treble_only = (treble_only_depth > 0)
            rules.append(rule)

        if treble_only_depth != 0:
            exit("ERROR: end of input while inside TREBLE_ONLY section")
        return rules

# neverallow_rule_to_test - takes a neverallow statement and transforms it into
# the output necessary to form a cts unit test in a java source file.
# returns: a string representing a generic test method based on this rule.
def neverallow_rule_to_test(rule, test_num):
    squashed_neverallow = rule.statement.replace("\n", " ")
    method  = SELinuxNeverallowTestFrame.src_method
    method = method.replace("testNeverallowRules()",
        "testNeverallowRules" + str(test_num) + "()")
    method = method.replace("$NEVERALLOW_RULE_HERE$", squashed_neverallow)
    method = method.replace(
        "$FULL_TREBLE_ONLY_BOOL_HERE$",
        "true" if rule.treble_only else "false")
    return method

if __name__ == "__main__":
    # check usage
    if len(sys.argv) != 3:
        print usage
        exit(1)
    input_file = sys.argv[1]
    output_file = sys.argv[2]

    src_header = SELinuxNeverallowTestFrame.src_header
    src_body = SELinuxNeverallowTestFrame.src_body
    src_footer = SELinuxNeverallowTestFrame.src_footer

    # grab the neverallow rules from the policy file and transform into tests
    neverallow_rules = extract_neverallow_rules(input_file)
    i = 0
    for rule in neverallow_rules:
        src_body += neverallow_rule_to_test(rule, i)
        i += 1

    with open(output_file, 'w') as out_file:
        out_file.write(src_header)
        out_file.write(src_body)
        out_file.write(src_footer)
