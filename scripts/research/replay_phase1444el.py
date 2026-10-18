"""Preparation replay remains unavailable until mandatory recovery gates pass."""


def replay() -> None:
    raise RuntimeError("REPLAY_GATES_NOT_SATISFIED")


if __name__ == "__main__":
    replay()
