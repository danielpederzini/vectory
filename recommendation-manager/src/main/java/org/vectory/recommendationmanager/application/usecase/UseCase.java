package org.vectory.recommendationmanager.application.usecase;

public interface UseCase<I, O> {

    O execute(I input);
}
