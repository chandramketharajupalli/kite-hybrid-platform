"""No replay is available until recovery validation passes."""


def replay() -> None:
    raise RuntimeError("REPLAY_NOT_AUTHORIZED_INCOMPLETE_VALIDATION")


if __name__ == "__main__":
    replay()
