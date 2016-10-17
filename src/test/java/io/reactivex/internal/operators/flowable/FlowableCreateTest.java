/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.operators.flowable;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.disposables.*;
import io.reactivex.exceptions.TestException;
import io.reactivex.functions.Cancellable;
import io.reactivex.internal.subscriptions.BooleanSubscription;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

public class FlowableCreateTest {

    @Test(expected = NullPointerException.class)
    public void sourceNull() {
        Flowable.create(null, FlowableEmitter.BackpressureMode.BUFFER);
    }

    @Test(expected = NullPointerException.class)
    public void modeNull() {
        Flowable.create(new FlowableOnSubscribe<Object>() {
            @Override
            public void subscribe(FlowableEmitter<Object> s) throws Exception { }
        }, null);
    }

    @Test
    public void basic() {
        final Disposable d = Disposables.empty();

        Flowable.<Integer>create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e.setDisposable(d);

                e.onNext(1);
                e.onNext(2);
                e.onNext(3);
                e.onComplete();
                e.onError(new TestException());
                e.onNext(4);
                e.onError(new TestException());
                e.onComplete();
            }
        }, FlowableEmitter.BackpressureMode.BUFFER)
        .test()
        .assertResult(1, 2, 3);

        assertTrue(d.isDisposed());
    }

    @Test
    public void basicWithCancellable() {
        final Disposable d1 = Disposables.empty();
        final Disposable d2 = Disposables.empty();

        Flowable.<Integer>create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e.setDisposable(d1);
                e.setCancellable(new Cancellable() {
                    @Override
                    public void cancel() throws Exception {
                        d2.dispose();
                    }
                });

                e.onNext(1);
                e.onNext(2);
                e.onNext(3);
                e.onComplete();
                e.onError(new TestException());
                e.onNext(4);
                e.onError(new TestException());
                e.onComplete();
            }
        }, FlowableEmitter.BackpressureMode.BUFFER)
        .test()
        .assertResult(1, 2, 3);

        assertTrue(d1.isDisposed());
        assertTrue(d2.isDisposed());
    }

    @Test
    public void basicWithError() {
        final Disposable d = Disposables.empty();

        Flowable.<Integer>create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e.setDisposable(d);

                e.onNext(1);
                e.onNext(2);
                e.onNext(3);
                e.onError(new TestException());
                e.onComplete();
                e.onNext(4);
                e.onError(new TestException());
            }
        }, FlowableEmitter.BackpressureMode.BUFFER)
        .test()
        .assertFailure(TestException.class, 1, 2, 3);

        assertTrue(d.isDisposed());
    }

    @Test
    public void basicSerialized() {
        final Disposable d = Disposables.empty();

        Flowable.<Integer>create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e = e.serialize();

                e.setDisposable(d);

                e.onNext(1);
                e.onNext(2);
                e.onNext(3);
                e.onComplete();
                e.onError(new TestException());
                e.onNext(4);
                e.onError(new TestException());
                e.onComplete();
            }
        }, FlowableEmitter.BackpressureMode.BUFFER)
        .test()
        .assertResult(1, 2, 3);

        assertTrue(d.isDisposed());
    }

    @Test
    public void basicWithErrorSerialized() {
        final Disposable d = Disposables.empty();

        Flowable.<Integer>create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e = e.serialize();

                e.setDisposable(d);

                e.onNext(1);
                e.onNext(2);
                e.onNext(3);
                e.onError(new TestException());
                e.onComplete();
                e.onNext(4);
                e.onError(new TestException());
            }
        }, FlowableEmitter.BackpressureMode.BUFFER)
        .test()
        .assertFailure(TestException.class, 1, 2, 3);

        assertTrue(d.isDisposed());
    }

    @Test
    public void wrap() {
        Flowable.fromPublisher(new Publisher<Integer>() {
            @Override
            public void subscribe(Subscriber<? super Integer> observer) {
                observer.onSubscribe(new BooleanSubscription());
                observer.onNext(1);
                observer.onNext(2);
                observer.onNext(3);
                observer.onNext(4);
                observer.onNext(5);
                observer.onComplete();
            }
        })
        .test()
        .assertResult(1, 2, 3, 4, 5);
    }

    @Test
    public void unsafe() {
        Flowable.unsafeCreate(new Publisher<Integer>() {
            @Override
            public void subscribe(Subscriber<? super Integer> observer) {
                observer.onSubscribe(new BooleanSubscription());
                observer.onNext(1);
                observer.onNext(2);
                observer.onNext(3);
                observer.onNext(4);
                observer.onNext(5);
                observer.onComplete();
            }
        })
        .test()
        .assertResult(1, 2, 3, 4, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsafeWithFlowable() {
        Flowable.unsafeCreate(Flowable.just(1));
    }

    @Test
    public void createNullValueBuffer() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.BUFFER)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void createNullValueLatest() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.LATEST)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void createNullValueError() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.ERROR)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void createNullValueDrop() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.DROP)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void createNullValueNone() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.NONE)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void createNullValueBufferSerialized() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e = e.serialize();
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.BUFFER)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void createNullValueLatestSerialized() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e = e.serialize();
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.LATEST)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void createNullValueErrorSerialized() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e = e.serialize();
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.ERROR)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void createNullValueDropSerialized() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e = e.serialize();
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.DROP)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void createNullValueNoneSerialized() {
        final Throwable[] error = { null };

        Flowable.create(new FlowableOnSubscribe<Integer>() {
            @Override
            public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                e = e.serialize();
                try {
                    e.onNext(null);
                    e.onNext(1);
                    e.onError(new TestException());
                    e.onComplete();
                } catch (Throwable ex) {
                    error[0] = ex;
                }
            }
        }, FlowableEmitter.BackpressureMode.NONE)
        .test()
        .assertFailure(NullPointerException.class);

        assertNull(error[0]);
    }

    @Test
    public void onErrorRace() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable<Object> source = Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    final FlowableEmitter<Object> f = e.serialize();

                    final TestException ex = new TestException();

                    Runnable r1 = new Runnable() {
                        @Override
                        public void run() {
                            f.onError(null);
                        }
                    };

                    Runnable r2 = new Runnable() {
                        @Override
                        public void run() {
                            f.onError(ex);
                        }
                    };

                    TestHelper.race(r1, r2, Schedulers.single());
                }
            }, m);

            List<Throwable> errors = TestHelper.trackPluginErrors();

            try {
                for (int i = 0; i < 500; i++) {
                    source
                    .test()
                    .assertFailure(Throwable.class);
                }
            } finally {
                RxJavaPlugins.reset();
            }
            assertFalse(errors.isEmpty());
        }
    }

    @Test
    public void onCompleteRace() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable<Object> source = Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    final FlowableEmitter<Object> f = e.serialize();

                    Runnable r1 = new Runnable() {
                        @Override
                        public void run() {
                            f.onComplete();
                        }
                    };

                    Runnable r2 = new Runnable() {
                        @Override
                        public void run() {
                            f.onComplete();
                        }
                    };

                    TestHelper.race(r1, r2, Schedulers.single());
                }
            }, m);

            for (int i = 0; i < 500; i++) {
                source
                .test()
                .assertResult();
            }
        }
    }

    @Test
    public void nullValue() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    e.onNext(null);
                }
            }, m)
            .test()
            .assertFailure(NullPointerException.class);
        }
    }

    @Test
    public void nullThrowable() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            System.out.println(m);
            Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    e.onError(null);
                }
            }, m)
            .test()
            .assertFailure(NullPointerException.class);
        }
    }

    @Test
    public void serializedConcurrentOnNextOnError() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    final FlowableEmitter<Object> f = e.serialize();

                    Runnable r1 = new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < 1000; i++) {
                                f.onNext(1);
                            }
                        }
                    };

                    Runnable r2 = new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < 100; i++) {
                                f.onNext(1);
                            }
                            f.onError(new TestException());
                        }
                    };

                    TestHelper.race(r1, r2, Schedulers.single());
                }
            }, m)
            .test()
            .assertSubscribed().assertNotComplete()
            .assertError(TestException.class);
        }
    }

    @Test
    public void callbackThrows() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    throw new TestException();
                }
            }, m)
            .test()
            .assertFailure(TestException.class);
        }
    }

    @Test
    public void nullValueSync() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    e.serialize().onNext(null);
                }
            }, m)
            .test()
            .assertFailure(NullPointerException.class);
        }
    }

    @Test
    public void createNullValue() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            final Throwable[] error = { null };

            Flowable.create(new FlowableOnSubscribe<Integer>() {
                @Override
                public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                    try {
                        e.onNext(null);
                        e.onNext(1);
                        e.onError(new TestException());
                        e.onComplete();
                    } catch (Throwable ex) {
                        error[0] = ex;
                    }
                }
            }, m)
            .test()
            .assertFailure(NullPointerException.class);

            assertNull(error[0]);
        }
    }

    @Test(expected = NullPointerException.class)
    public void nullArgument() {
        Flowable.create(null, FlowableEmitter.BackpressureMode.NONE);
    }

    @Test
    public void onErrorCrash() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    Disposable d = Disposables.empty();
                    e.setDisposable(d);
                    try {
                        e.onError(new IOException());
                        fail("Should have thrown");
                    } catch (TestException ex) {
                        // expected
                    }
                    assertTrue(d.isDisposed());
                }
            }, m)
            .subscribe(new Subscriber<Object>() {
                @Override
                public void onSubscribe(Subscription d) {
                }

                @Override
                public void onNext(Object value) {
                }

                @Override
                public void onError(Throwable e) {
                    throw new TestException();
                }

                @Override
                public void onComplete() {
                }
            });
        }
    }

    @Test
    public void onCompleteCrash() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    Disposable d = Disposables.empty();
                    e.setDisposable(d);
                    try {
                        e.onComplete();
                        fail("Should have thrown");
                    } catch (TestException ex) {
                        // expected
                    }
                    assertTrue(d.isDisposed());
                }
            }, m)
            .subscribe(new Subscriber<Object>() {
                @Override
                public void onSubscribe(Subscription d) {
                }

                @Override
                public void onNext(Object value) {
                }

                @Override
                public void onError(Throwable e) {
                }

                @Override
                public void onComplete() {
                    throw new TestException();
                }
            });
        }
    }

    @Test
    public void createNullValueSerialized() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            final Throwable[] error = { null };

            Flowable.create(new FlowableOnSubscribe<Integer>() {
                @Override
                public void subscribe(FlowableEmitter<Integer> e) throws Exception {
                    e = e.serialize();
                    try {
                        e.onNext(null);
                        e.onNext(1);
                        e.onError(new TestException());
                        e.onComplete();
                    } catch (Throwable ex) {
                        error[0] = ex;
                    }
                }
            }, m)
            .test()
            .assertFailure(NullPointerException.class);

            assertNull(error[0]);
        }
    }

    @Test
    public void nullThrowableSync() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    e.serialize().onError(null);
                }
            }, m)
            .test()
            .assertFailure(NullPointerException.class);
        }
    }

    @Test
    public void serializedConcurrentOnNext() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    final FlowableEmitter<Object> f = e.serialize();

                    Runnable r1 = new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < 1000; i++) {
                                f.onNext(1);
                            }
                        }
                    };

                    TestHelper.race(r1, r1, Schedulers.single());
                }
            }, m)
            .take(1000)
            .test()
            .assertSubscribed().assertValueCount(1000).assertComplete().assertNoErrors();
        }
    }

    @Test
    public void serializedConcurrentOnNextOnComplete() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            TestSubscriber<Object> to = Flowable.create(new FlowableOnSubscribe<Object>() {
                @Override
                public void subscribe(FlowableEmitter<Object> e) throws Exception {
                    final FlowableEmitter<Object> f = e.serialize();

                    Runnable r1 = new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < 1000; i++) {
                                f.onNext(1);
                            }
                        }
                    };

                    Runnable r2 = new Runnable() {
                        @Override
                        public void run() {
                            for (int i = 0; i < 100; i++) {
                                f.onNext(1);
                            }
                            f.onComplete();
                        }
                    };

                    TestHelper.race(r1, r2, Schedulers.single());
                }
            }, m)
            .test()
            .assertSubscribed().assertComplete()
            .assertNoErrors();

            int c = to.valueCount();
            assertTrue("" + c, c >= 100);
        }
    }

    @Test
    public void serialized() {
        for (FlowableEmitter.BackpressureMode m : FlowableEmitter.BackpressureMode.values()) {
            List<Throwable> errors = TestHelper.trackPluginErrors();
            try {
                Flowable.create(new FlowableOnSubscribe<Object>() {
                    @Override
                    public void subscribe(FlowableEmitter<Object> e) throws Exception {
                        FlowableEmitter<Object> f = e.serialize();

                        assertSame(f, f.serialize());

                        assertFalse(f.isCancelled());

                        final int[] calls = { 0 };

                        f.setCancellable(new Cancellable() {
                            @Override
                            public void cancel() throws Exception {
                                calls[0]++;
                            }
                        });

                        e.onComplete();

                        assertTrue(f.isCancelled());

                        assertEquals(1, calls[0]);
                    }
                }, m)
                .test()
                .assertResult();

                assertTrue(errors.toString(), errors.isEmpty());
            } finally {
                RxJavaPlugins.reset();
            }
        }
    }

}
