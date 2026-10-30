import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

# The listener and watchdog run from the swarm's own clone, which they keep up to date,
# so new swarm code reaches every PC with no git pull. Anything else uses this copy.
if sys.argv[1:2] in (["listener"], ["watchdog"]):
    home = os.environ.get("SWARM_HOME") or os.path.join(os.path.expanduser("~"), ".swarm")
    clone = os.path.join(home, "repo", "swarm")
    if os.path.isfile(os.path.join(clone, "swarmlib", "cli.py")):
        sys.path.insert(0, clone)

from swarmlib.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())
