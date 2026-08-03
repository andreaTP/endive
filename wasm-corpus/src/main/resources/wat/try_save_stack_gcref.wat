(module
  ;; Test that GC ref values below a try_table scope are preserved.
  ;; This mirrors the pattern in Kotlin's main() where a String literal
  ;; is pushed, then a try_table block is entered, and after the block
  ;; the String is used. Issue #139.

  (type $point (struct (field $x (mut i32)) (field $y (mut i32))))
  (tag $e (param i32))

  (func $make_point (param $x i32) (param $y i32) (result (ref $point))
    (struct.new $point (local.get $x) (local.get $y))
  )

  (func $do_throw (param $val i32)
    (throw $e (local.get $val))
  )

  ;; Push a GC ref below try_table, exception fires, ref must survive in catch.
  (func (export "gcref-below-try-catch") (result i32)
    (call $make_point (i32.const 42) (i32.const 99))  ;; ref is below try scope
    (block $h (result i32)
      (try_table (catch $e $h)
        (call $do_throw (i32.const 0))
      )
      (unreachable)
    )
    (drop)
    (struct.get $point $x)  ;; should be 42
  )

  ;; Push a GC ref below try_table, normal path (no exception), ref must survive.
  (func (export "gcref-below-try-normal") (result i32)
    (call $make_point (i32.const 77) (i32.const 88))  ;; ref is below try scope
    (block $h (result i32)
      (try_table (result i32) (catch $e $h)
        (i32.const 55)  ;; just produce a value, no throw
      )
    )
    (drop)  ;; drop the block result
    (struct.get $point $x)  ;; should be 77
  )

  ;; GC ref below nested blocks with try_table — mirrors Kotlin main() pattern:
  ;; push ref, block { block { try_table { ... } } }, use ref
  (func (export "gcref-below-nested-try") (result i32)
    (call $make_point (i32.const 33) (i32.const 44))  ;; ref below everything
    (block (result i32)  ;; outer block
      (block $h (result i32)  ;; handler target
        (try_table (result i32) (catch $e $h)
          (i32.const 100)  ;; normal result
        )
      )
    )
    (drop)  ;; drop block result
    (struct.get $point $x)  ;; should be 33
  )

  ;; Two values below try: one i32, one GC ref — tests mixed stack save/restore.
  (func (export "mixed-stack-below-try") (result i32)
    (i32.const 100)  ;; i32 value below
    (call $make_point (i32.const 50) (i32.const 60))  ;; GC ref above the i32
    (block $h (result i32)
      (try_table (catch $e $h)
        (call $do_throw (i32.const 7))
      )
      (unreachable)
    )
    (drop)  ;; drop caught value
    (struct.get $point $x)  ;; should be 50
    (i32.add)  ;; 100 + 50 = 150
  )

  ;; Mirrors the exact pattern from Kotlin main() in issue #139:
  ;; Push GC ref, enter block { block { block { block { try_table { br 3 } } } } },
  ;; then use the GC ref. Normal path with multi-level br from try body.
  (func (export "gcref-below-deep-try-br") (result i32)
    (call $make_point (i32.const 42) (i32.const 99))  ;; GC ref below everything
    (block (result (ref null $point))  ;; @1 outer
      (block (result (ref null $point))  ;; @2 target of br
        (block $h (result i32)  ;; @3 catch handler
          (block  ;; @4
            (try_table (catch $e $h)  ;; @5
              ;; Normal path: produce a result and br out to @2
              (call $make_point (i32.const 10) (i32.const 20))
              (br 3)  ;; jump to @2 (skipping @5, @4, @3)
            )
            (unreachable)
          )
          (unreachable)
        )
        (drop)  ;; in catch handler: drop the caught i32
        (call $make_point (i32.const 0) (i32.const 0))  ;; push dummy
      )
    )
    (drop)  ;; drop result from blocks
    (struct.get $point $x)  ;; should be 42 (the original ref)
  )

  ;; Bug B: catch with GC ref tag param — CATCH_UNBOX_PARAMS needs 3 temp slots.
  ;; Value below try must survive when the catch handler unboxes a GC ref param.
  (tag $gc_tag (param (ref $point)))

  (func $throw_point (param $p (ref $point))
    (throw $gc_tag (local.get $p))
  )

  (func (export "catch-gcref-tag-param") (result i32)
    (i32.const 100)  ;; value below try
    (block $h (result (ref $point))
      (try_table (catch $gc_tag $h)
        (call $throw_point (call $make_point (i32.const 77) (i32.const 88)))
      )
      (unreachable)
    )
    ;; stack: [100, caught_point]
    (struct.get $point $x)  ;; should be 77
    (i32.add)  ;; 100 + 77 = 177
  )
)
