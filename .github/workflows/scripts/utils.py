"""
This module provides common functions for GitHub Actions 
automation scripts.
"""

import os
import subprocess
import sys


def run_command(cmd):
    """ Run a shell command and return the output. """
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Error running command: {cmd}")
        print(f"Error: {result.stderr}")
        sys.exit(0)
    return result.stdout.strip()


def read_file(file_path):
    """Read content from a file."""
    try:
        with open(file_path, 'r') as f:
            return f.read()
    except Exception as e:
        print(f"Error reading file '{file_path}': {e}")
        sys.exit(0)


def write_file(file_path, content):
    """Write content to a file."""
    try:
        with open(file_path, 'w') as f:
            f.write(content)
    except Exception as e:
        print(f"Error writing to file '{file_path}': {e}")
        sys.exit(0)


def set_github_output(name, value):
    """ Set GitHub Actions output variable. """
    github_output = os.environ.get('GITHUB_OUTPUT')
    if github_output:
        with open(github_output, 'a', encoding='utf-8') as f:
            f.write(f"{name}={value}\n")

def set_multi_line_github_output(name, value):
    """ Set GitHub Actions output variable with multi-line. """
    github_output = os.environ.get('GITHUB_OUTPUT')
    if github_output:
        with open(github_output, 'a', encoding='utf-8') as f:
            delimiter = f"EOF_{name}_{os.urandom(8).hex()}"
            f.write(f"{name}<<{delimiter}\n")
            f.write(f"{value}\n")
            f.write(f"{delimiter}\n")

def has_trailing_empty_lines(lines_list):
    """Check if list has empty lines at the end"""
    return lines_list and not lines_list[-1].strip()

def remove_trailing_empty_lines(lines_list):
    """Remove trailing empty lines from a list of lines"""
    while has_trailing_empty_lines(lines_list):
        lines_list.pop()
    return lines_list