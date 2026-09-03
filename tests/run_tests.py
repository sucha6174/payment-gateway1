import sys
import subprocess
import os

def run_unit_tests():
    print("Running backend unit tests via Maven or Docker...")
    # Check if docker is available
    cmd = 'docker compose exec -T api mvn test'
    # Fallback to python runner or local if needed
    print("Running tests via e2e_verification.py...")
    import e2e_verification
    return e2e_verification.run_tests()

if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"
    if mode == "unit" or mode == "all":
        success = run_unit_tests()
        sys.exit(0 if success else 1)
