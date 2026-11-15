import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import backend_matrix as bm  # noqa: E402

TWO = ['notification-service', 'scheduling']
ONE = ['scheduling']


class PlanTest(unittest.TestCase):

    def test_nothing_outside_the_backend_triggers_nothing(self):
        r = bm.plan(['frontend/src/App.tsx', 'README.md', '.github/workflows/frontend.yml', 'docs/db-conventions.md'], TWO)
        self.assertEqual(r, {'run': False, 'platform': False, 'services': []})

    def test_empty_change_list_triggers_nothing(self):
        self.assertFalse(bm.plan([], TWO)['run'])

    def test_a_change_in_one_service_builds_only_that_service(self):
        r = bm.plan(['backend/services/notification-service/src/main/java/A.java'], TWO)
        self.assertEqual(r, {'run': True, 'platform': False, 'services': ['notification-service']})

    def test_changes_in_two_services_build_both_without_platform(self):
        r = bm.plan(['backend/services/notification-service/pom.xml', 'backend/services/scheduling/x.yml'], TWO)
        self.assertEqual(r, {'run': True, 'platform': False, 'services': ['notification-service', 'scheduling']})

    def test_a_platform_change_builds_every_service_and_the_platform(self):
        r = bm.plan(['backend/platform/p360-platform-starter/src/main/java/A.java'], TWO)
        self.assertEqual(r, {'run': True, 'platform': True, 'services': TWO})

    def test_shared_build_files_build_everything(self):
        for path in ['backend/pom.xml', 'backend/.mvn/wrapper/maven-wrapper.properties', 'backend/mvnw',
                     'backend/Dockerfile', '.java-version', '.github/actions/setup-backend/action.yml']:
            r = bm.plan([path], TWO)
            self.assertEqual((r['platform'], r['services']), (True, TWO), path)

    def test_contracts_docker_and_the_pipeline_build_everything(self):
        for path in ['api/openapi.json', 'api/events/envelope.schema.json', 'docker/edge/routing-table.csv',
                     'docker/postgres/init/01-roles.sh', 'docker-compose.yml', '.github/workflows/backend.yml',
                     '.github/scripts/backend_matrix.py', 'docs/adr/ADR-B01-postgresql-wersja-i-role.md']:
            r = bm.plan([path], TWO)
            self.assertEqual((r['run'], r['platform'], r['services']), (True, True, TWO), path)

    def test_an_unknown_backend_path_is_treated_as_shared(self):
        r = bm.plan(['backend/README.md'], TWO)
        self.assertEqual((r['platform'], r['services']), (True, TWO))

    def test_a_shared_change_wins_over_a_single_service_change(self):
        r = bm.plan(['backend/services/scheduling/A.java', 'backend/platform/p360-shared-core/B.java'], TWO)
        self.assertEqual((r['platform'], r['services']), (True, TWO))

    def test_a_removed_or_unknown_service_builds_nothing(self):
        r = bm.plan(['backend/services/deleted-service/src/A.java'], TWO)
        self.assertEqual(r, {'run': False, 'platform': False, 'services': []})

    def test_force_all_builds_everything(self):
        r = bm.plan([], TWO, force_all=True)
        self.assertEqual(r, {'run': True, 'platform': True, 'services': TWO})

    def test_windows_separators_and_blank_lines_are_tolerated(self):
        r = bm.plan(['', '  ', 'backend\\services\\scheduling\\A.java'], ONE)
        self.assertEqual(r, {'run': True, 'platform': False, 'services': ONE})


class CliTest(unittest.TestCase):

    def run_cli(self, stdin, *args, services=('scheduling',)):
        with tempfile.TemporaryDirectory() as tmp:
            for name in services:
                os.makedirs(os.path.join(tmp, name))
                open(os.path.join(tmp, name, 'pom.xml'), 'w').close()
            os.makedirs(os.path.join(tmp, 'not-a-service'))  # katalog bez pom.xml
            out = subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), 'backend_matrix.py'),
                                  '--services-dir', tmp, *args], input=stdin, capture_output=True, text=True,
                                 check=True).stdout
        return dict(line.split('=', 1) for line in out.splitlines())

    def test_output_format_for_github_output(self):
        out = self.run_cli('backend/services/scheduling/A.java\n')
        self.assertEqual(out, {'run': 'true', 'platform': 'false', 'services': '["scheduling"]'})

    def test_only_directories_with_a_pom_are_services(self):
        out = self.run_cli('', '--all', services=('a-service', 'b-service'))
        self.assertEqual(out['services'], '["a-service","b-service"]')

    def test_no_backend_change(self):
        out = self.run_cli('frontend/package.json\n')
        self.assertEqual(out, {'run': 'false', 'platform': 'false', 'services': '[]'})


if __name__ == '__main__':
    unittest.main()
