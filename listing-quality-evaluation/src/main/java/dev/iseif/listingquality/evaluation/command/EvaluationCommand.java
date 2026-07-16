package dev.iseif.listingquality.evaluation.command;

public sealed interface EvaluationCommand permits CollectCommand, CompareCommand {}
