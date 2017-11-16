import core

class AdapterTest(core.MyTestCase):

    def test_something(self):
        with self.master.cursor() as a, self.cs.cursor() as b:
            a.execute("SELECT 1")
            b.execute("SELECT 1")
            self.assertEqual(a.fetchone(), b.fetchone())

    def test_something_else(self):
        with self.master.cursor() as a, self.cs.cursor() as b:
            a.execute("SELECT 2")
            b.execute("SELECT 1")
            self.assertNotEqual(a.fetchone(), b.fetchone())
